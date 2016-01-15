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

import java.net.InetAddress
import java.util.Properties

import scala.util.Try
import kafka.producer.ProducerConfig
import kafka.server.KafkaConfig

/**
 * TODO remove hard coded, defaults, add config.
 */
class Settings extends EmbeddedIO {
  import scala.collection.JavaConverters._
  import Embedded._

  val zkConf = ZookeeperConfiguration()

  val kafkaConf = KafkaConfiguration()

  def kafkaParams(groupId: String): Map[String, String] = Map(
    "zookeeper.connect" -> DefaultZookeeperConnect,
    "group.id" -> groupId,
    "auto.offset.reset" -> "smallest"
  )

  def kafkaConfig(
    kafkaConf: KafkaConfiguration,
    zkConf: ZookeeperConfiguration
  ): KafkaConfig = {
    val (host, port) = kafkaConf.hostPortUnsafe.head

    val c = Map(
      "broker.id" -> "0",
      "host.name" -> "127.0.0.1", //localhost
      "port" -> port.toString,
      "advertised.host.name" -> "127.0.0.1",
      "advertised.port" -> port.toString,
      "log.dir" -> createTempDir("kafka-embedded-tmp").getAbsolutePath,
      "zookeeper.connect" -> zkConf.connectTo,
      "replica.high.watermark.checkpoint.interval.ms" -> "5000",
      "log.flush.interval.messages" -> "1",
      "replica.socket.timeout.ms" -> "1500",
      "controlled.shutdown.enable" -> "false",
      "auto.leader.rebalance.enable" -> "false"
    )

    val props = new Properties()
    props.putAll(c.asJava)
    new KafkaConfig(props)
  }

  def producerConfig[A](
    brokerAddress: String,
    serializer: Class[A]
  ): ProducerConfig = {
    val c = Map(
      "metadata.broker.list" -> brokerAddress,
      // wait for all in-sync replicas to ack sends
      "request.required.acks" -> "-1",
      "serializer.class" -> serializer.getName
    )

    val props = new Properties()
    props.putAll(c.asJava)
    new ProducerConfig(props)
  }

}

/* TODO remove default args */
object Embedded {

  final val DefaultZkPort: Int = 2181

  final val DefaultKafkaPort: Int = 9092

  final val DefaultHost: InetAddress = InetAddress.getLocalHost

  final val DefaultHostString: String = "127.0.0.1" //DefaultHost.getHostAddress

  final val DefaultZookeeperConnect: String = s"$DefaultHostString:$DefaultZkPort"

  final val DefaultKafkaConnect: String = s"$DefaultHostString:$DefaultKafkaPort"

  sealed trait ServerConfig extends Serializable

  final case class ZookeeperConfiguration(
      connectTo: String = DefaultZookeeperConnect, //"localhost"
      tickTime: Int = 3000,
      connectionTimeout: Int = 60000,
      sessionTimeout: Int = 6000
  ) extends ServerConfig {

    /** Use at your own peril until validation added. */
    def hostPortUnsafe: (String, Int) = {
      val splits = connectTo.split(":")
      (splits(0), splits(1).toInt)
    }

  }

  final case class KafkaConfiguration(connectTo: String) extends ServerConfig {

    /** Use at your own peril until validation added. */
    def hostPortUnsafe: Array[(String, Int)] =
      Try(for {
        hostPort <- connectTo.split(",")
      } yield {
        val splits = hostPort.split(":")
        (splits(0), splits(1).toInt)
      }).getOrElse(Array.empty)
  }

  object KafkaConfiguration {

    def apply(): KafkaConfiguration =
      KafkaConfiguration(DefaultKafkaConnect)

    def apply(host: String, port: Int): KafkaConfiguration = {
      KafkaConfiguration(s"$host:$port")
    }
  }
}
