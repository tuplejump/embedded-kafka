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

import java.util.concurrent.atomic.{ AtomicReference, AtomicBoolean }

import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.clients.producer.{ProducerRecord, KafkaProducer}
import kafka.admin.AdminUtils
import kafka.producer.ProducerConfig
import kafka.server.{ KafkaConfig, KafkaServer }
import org.I0Itec.zkclient.ZkClient

final class EmbeddedKafka(kafkaConnect: String, zkConnect: String)
  extends Settings with Assertions with Logging {

  def this() = this(DefaultKafkaConnect, DefaultZookeeperConnect)

  /** Should an error occur, make sure it shuts down. */
  sys.runtime.addShutdownHook(new Thread("Shutting down embedded kafka") {
    override def run(): Unit = shutdown()
  })

  val logDir = EmbeddedIO.logsDir
  val snapDir = EmbeddedIO.createTempDir("zk-snapshots")
  val dataDir = EmbeddedIO.createTempDir("zk-data")

  val config = brokerConfig(kafkaConnect, zkConnect, dataDir.getAbsolutePath)

  val kafkaConfig: KafkaConfig = new KafkaConfig(mapToProps(config))

  /** hard-coded for Strings only so far. Roadmap: making configurable. */
  val producerConfig: ProducerConfig = new ProducerConfig(mapToProps(
    super.producerConfig(kafkaConnect, classOf[StringSerializer], classOf[StringSerializer])
  ))

  private val _isRunning = new AtomicBoolean(false)

  private val _zookeeper = new AtomicReference[Option[EmbeddedZookeeper]](None)

  private val _zkClient = new AtomicReference[Option[ZkClient]](None)

  private val _server = new AtomicReference[Option[KafkaServer]](None)

  private val _producer = new AtomicReference[Option[KafkaProducer[String, String]]](None)

  def server: KafkaServer = _server.get.getOrElse {
    logger.info("Attempt to call server before starting EmbeddedKafka instance. Starting automatically...")
    start()
    eventually(5000, 500)(assert(isRunning, "Kafka must be running."))
    _server.get.getOrElse(throw new IllegalStateException("Kafka server not initialized."))
  }

  def isRunning: Boolean = {
    _zookeeper.get.exists(_.isRunning) && _server.get.isDefined && _isRunning.get() // a better way?
  }

  def producer: KafkaProducer[String, String] = _producer.get.getOrElse {
    require(isRunning, "Attempt to call producer before starting EmbeddedKafka instance. Call EmbeddedKafka.start() first.")
    val p = try new KafkaProducer[String, String](producerConfig.props.props) catch {
      case e: Throwable => logger.error(s"Unable to create producer.", e); throw e
    }
    _producer.set(Some(p))
    p
  }

  /** Starts the embedded Zookeeper server and Kafka brokers. */
  def start(): Unit = {
    require(_zookeeper.get.forall(!_.isRunning), "Zookeeper should not be running prior to calling 'start'.")
    require(_server.get.isEmpty, "KafkaServer should not be running prior to calling 'start'.")

    val zk = new EmbeddedZookeeper(zkConnect, 6000, snapDir, dataDir)
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
    AdminUtils.createTopic(server.zkUtils, topic, numPartitions, replicationFactor)
    awaitPropagation(topic, 0)
  }

  /** Send the messages to the Kafka broker */
  def sendMessages(topic: String, messageToFreq: Map[String, Int]): Unit = {
    val messages = messageToFreq.flatMap { case (s, freq) => Seq.fill(freq)(s) }.toArray
    sendMessages(topic, messages)
  }

  /** Send the array of messages to the Kafka broker */
  def sendMessages(topic: String, messages: Iterable[String]): Unit =
    for {
      message <- messages
    } producer.send(new ProducerRecord[String, String](topic, message))

  /* TODO with a key and as [K,V] and Array[Byte]*/
  private val toRecord = (topic: String, group: Option[String], message: String) => group match {
    case Some(k) => new ProducerRecord[String, String](topic, k, message)
    case _       => new ProducerRecord[String, String](topic, message)
  }

  private def awaitPropagation(topic: String, partition: Int): Unit = {
    eventually(10000, 1000) {
      assert(AdminUtils.topicExists(server.zkUtils, topic))
      logger.info(s"Topic [$topic] created.")
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
