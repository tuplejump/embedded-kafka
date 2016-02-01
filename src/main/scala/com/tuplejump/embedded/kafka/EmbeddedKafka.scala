/*
 * Copyright 2016 Tuplejump
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tuplejump.embedded.kafka

import java.util.Properties
import java.util.concurrent.atomic.{ AtomicReference, AtomicBoolean }

import scala.collection.JavaConverters._
import kafka.admin.AdminUtils
import kafka.api.Request
import kafka.serializer.StringEncoder
import kafka.producer.{ ProducerConfig, KeyedMessage, Producer }
import kafka.server.{ KafkaConfig, KafkaServer }
import kafka.utils.ZkUtils
import org.I0Itec.zkclient.ZkClient

final class EmbeddedKafka(kafkaConnect: String, zkConnect: String)
  extends EmbeddedIO with Settings with Assertions with Logging {

  def this() = this(DefaultKafkaConnect, DefaultZookeeperConnect)

  /** Should an error occur, make sure it shuts down. */
  Runtime.getRuntime.addShutdownHook(new Thread("Shutting down embedded kafka") {
    override def run() { shutdown() }
  })

  val config = brokerConfig(kafkaConnect, zkConnect)

  val kafkaConfig: KafkaConfig = {
    val props = new Properties()
    props.putAll(config.asJava)
    new KafkaConfig(props)
  }

  val producerConfig: ProducerConfig = super.producerConfig(
    config, classOf[StringEncoder], classOf[StringEncoder]
  )

  private val _isRunning = new AtomicBoolean(false)

  private val _zookeeper = new AtomicReference[Option[EmbeddedZookeeper]](None)

  private val _zkClient = new AtomicReference[Option[ZkClient]](None)

  private val _server = new AtomicReference[Option[KafkaServer]](None)

  private val _producer = new AtomicReference[Option[Producer[String, String]]](None)

  def server: KafkaServer = _server.get.getOrElse {
    logger.info("Attempt to call server before starting EmbeddedKafka instance. Starting automatically...")
    start()
    eventually(5000, 500)(assert(isRunning, "Kafka must be running."))
    _server.get.getOrElse(throw new IllegalStateException("Kafka server not initialized."))
  }

  def isRunning: Boolean = {
    _zookeeper.get.exists(_.isRunning) && _server.get.isDefined && _isRunning.get() // a better way?
  }

  def producer: Producer[String, String] = _producer.get.getOrElse {
    require(isRunning, "Attempt to call producer before starting EmbeddedKafka instance. Call EmbeddedKafka.start() first.")
    val p = new Producer[String, String](producerConfig)
    _producer.set(Some(p))
    p
  }

  /** Starts the embedded Zookeeper server and Kafka brokers. */
  def start(): Unit = {

    require(_zookeeper.get.forall(!_.isRunning), "Zookeeper should not be running prior to calling 'start'.")
    require(_server.get.isEmpty, "KafkaServer should not be running prior to calling 'start'.")

    val zk = new EmbeddedZookeeper(connectTo = zkConnect, tickTime = 6000)
    zk.start()
    eventually(5000, 500) {
      require(zk.isRunning, "Zookeeper must be started before proceeding with setup.")
      _zookeeper.set(Some(zk))
    }

    logger.info("Starting ZkClient")
    _zkClient.set(Some(new ZkClient(zkConnect, 6000, 60000, DefaultStringSerializer)))


    logger.info("Starting KafkaServer")
    _server.set(Some(new KafkaServer(kafkaConfig)))
    server.startup()

    _isRunning.set(true) // TODO add a test
  }

  /** Creates a Kafka topic and waits until it is propagated to the cluster: 1,1 */
  def createTopic(topic: String, numPartitions: Int, replicationFactor: Int): Unit = {
    AdminUtils.createTopic(server.zkUtils, topic, numPartitions, replicationFactor) //TODO add topic config
    awaitPropagation(topic, 0)
  }

  /** Send the messages to the Kafka broker */
  def sendMessages(topic: String, messageToFreq: Map[String, Int]): Unit = {
    val messages = messageToFreq.flatMap { case (s, freq) => Seq.fill(freq)(s) }.toArray
    sendMessages(topic, messages)
  }

  /** Send the array of messages to the Kafka broker */
  def sendMessages(topic: String, messages: Iterable[String]): Unit = {
    producer.send(messages.toArray.map { new KeyedMessage[String, String](topic, _) }: _*)
  }

  private def awaitPropagation(topic: String, partition: Int): Unit = {
    def isPropagated = server.apis.metadataCache.getPartitionInfo(topic, partition) match {
      case Some(partitionState) =>
        val leaderAndInSyncReplicas = partitionState.leaderIsrAndControllerEpoch.leaderAndIsr

        ZkUtils.getTopicPartitionLeaderAndIsrPath(topic, partition).nonEmpty &&
          Request.isValidBrokerId(leaderAndInSyncReplicas.leader) &&
          leaderAndInSyncReplicas.isr.nonEmpty

      case _ =>
        false
    }
    eventually(10000, 100) {
      assert(isPropagated, s"Partition [$topic, $partition] metadata not propagated after timeout")
    }
  }

  /** Shuts down the embedded servers.*/
  def shutdown(): Unit = try {
    logger.info(s"Shutting down Kafka server.")

    for (v <- _producer.get) v.close()
    for (v <- _server.get) {
      //https://issues.apache.org/jira/browse/KAFKA-1887 ?
      v.kafkaController.shutdown()
      v.getLogManager.cleanupLogs()
      v.shutdown()
      v.awaitShutdown()
      //v.config.logDirs.foreach { f => deleteRecursively(new JFile(f)) }

    }

    for (v <- _zkClient.get) v.close()

    for (v <- _zookeeper.get) v.shutdown()

    _producer.set(None)
    _server.set(None)
    _zkClient.set(None)
    _zookeeper.set(None)
  } catch {
    case e: Throwable =>
      logger.error("Error shutting down.", e)
  }
}
