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

import kafka.producer.ProducerConfig
import kafka.server.KafkaConfig
import Embedded._

/**
 * TODO remove hard coded, defaults, add config.
 */
class Settings(val zkConf: ZookeeperConfiguration, val kafkaConf: KafkaConfiguration) extends EmbeddedIO {

  def this(values: Map[String,String]) =
    this(ZookeeperConfiguration(), KafkaConfiguration(values))

  def this() =
    this(ZookeeperConfiguration(), KafkaConfiguration())

  import scala.collection.JavaConverters._

  val kafkaConfig: KafkaConfig = {
    val props = new Properties()
    props.putAll(kafkaConf.settings.asJava)
    new KafkaConfig(props)
  }

  def producerConfig[A](brokerAddress: String, serializer: Class[A]): ProducerConfig = {
    val c = Map(
      "metadata.broker.list" -> brokerAddress,
      "request.required.acks" -> "-1",
      "serializer.class" -> serializer.getName)

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

  final val DefaultGroupId: String = s"consumer-${scala.util.Random.nextInt(10000)}"

  sealed trait ServerConfig extends Serializable

  final case class ZookeeperConfiguration(
      connectTo: String,
      tickTime: Int,
      connectionTimeout: Int,
      sessionTimeout: Int = 6000
  ) extends ServerConfig {

    /** Use at your own peril until validation added. */
    def hostPortUnsafe: (String, Int) = {
      val splits = connectTo.split(":")
      (splits(0), splits(1).toInt)
    }

  }

  object ZookeeperConfiguration {

    def apply(): ZookeeperConfiguration =
      ZookeeperConfiguration(DefaultZookeeperConnect, 3000, 60000, 6000)
  }

  final case class KafkaConfiguration(settings: Map[String, String]) extends ServerConfig {

    def connectTo: (String, String) = {
      val host = settings.getOrElse("host.name", DefaultHostString)
      val port = settings.getOrElse("port", DefaultKafkaPort.toString)
      (host, port)
    }

    def kafkaParams(groupId: Option[String]): Map[String, String] =
      settings ++ Map(
        "group.id" -> groupId.getOrElse(DefaultGroupId),
        "auto.offset.reset" -> "largest")
  }

  object KafkaConfiguration extends EmbeddedIO {

    def settings(host: String, port: Int, zkConnect: String): Map[String,String] =
      Map(
        "broker.id" -> "0",
        "host.name" -> host,
        "port" -> port.toString,
        "metadata.broker.list" -> "host",
        "advertised.host.name" -> host,
        "advertised.port" -> port.toString,
        "log.dir" -> createTempDir("kafka-embedded-tmp").getAbsolutePath,
        "zookeeper.connect" -> zkConnect,
        "replica.high.watermark.checkpoint.interval.ms" -> "5000",
        "log.flush.interval.messages" -> "1",
        "replica.socket.timeout.ms" -> "1500",
        "controlled.shutdown.enable" -> "false",
        "auto.leader.rebalance.enable" -> "false")

    def apply(host: Option[String] = None,
              port: Option[Int] = None,
              zkConnect: Option[String] = None): KafkaConfiguration = KafkaConfiguration(
      settings(
        host.getOrElse(DefaultHostString),
        port.getOrElse(DefaultKafkaPort),
        zkConnect.getOrElse(DefaultZookeeperConnect)))
  }
}
