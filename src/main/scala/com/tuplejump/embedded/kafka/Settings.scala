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

import scala.collection.JavaConverters._
import kafka.producer.ProducerConfig

/**
 * TODO remove hard coded, defaults, add config.
 */
trait Settings extends EmbeddedIO {

  def brokerConfig(kafkaConnect: String, zkConnect: String): Map[String,String] =
    Map(
      // "broker.id" -> brokerId.toString,
      "metadata.broker.list" -> kafkaConnect,
      "log.cleanup.policy" -> "delete",
      "log.dirs" -> createTempDir("kafka-embedded-tmp").getAbsolutePath,
      "zookeeper.connect" -> zkConnect,
      //"zookeeper.connection.timeout.ms" -> "1000",//	The max time that the client waits to establish a connection to zookeeper. If not set, the value in zookeeper.session.timeout.ms is used	int	null		high
      //"zookeeper.session.timeout.ms" -> "1000",
      "replica.high.watermark.checkpoint.interval.ms" -> "5000",
      "log.flush.interval.messages" -> "1",
      "replica.socket.timeout.ms" -> "1500",
      "controlled.shutdown.enable" -> "false",
      "auto.leader.rebalance.enable" -> "false")

  def producerConfig(brokerConfig: Map[String,String], serializer: Class[_]): ProducerConfig = {
    /*
      "bootstrap.servers" -> brokerConfig("metadata.broker.list"),
      //"client.id" -> "",
      "key.serializer" -> kSerializer.getName,
      "value.serializer" -> vSerializer.getName)*/

    val c = brokerConfig ++ Map(
      "producer.type" -> "async",
      "request.required.acks" -> "-1",
      "serializer.class" -> serializer.getName)


    val props = new Properties()
    props.putAll(c.asJava)
    new ProducerConfig(props)
  }

  /** offsetPolicy = new consumer: latest,earliest,none */
  def consumerConfig(group: String,
                     kafkaConnect: String,
                     zkConnect: String,
                     offsetPolicy: String,
                     autoCommitEnabled: Boolean,
                     kDeserializer: Class[_],
                     vDeserializer: Class[_]): Map[String,String] =
    Map(
      //consumer.timeout.ms
      "zookeeper.connect" -> zkConnect,
      "bootstrap.servers" -> kafkaConnect,
      "group.id" -> group,
      "auto.offset.reset" -> offsetPolicy,
      "enable.auto.commit" -> autoCommitEnabled.toString,
      "auto.commit.interval.ms" -> "1000",
      "session.timeout.ms" -> "30000",
      "key.deserializer" -> kDeserializer.getName,
      "value.deserializer" -> vDeserializer.getName)

  def kafkaParams(group: String,
                  kafkaConnect: String,
                  zkConnect: String,
                  offsetPolicy: String,
                  autoCommitEnabled: Boolean,
                  kDeserializer: Class[_],
                  vDeserializer: Class[_]): Map[String, String] =
    consumerConfig(
      group, kafkaConnect, zkConnect, offsetPolicy, autoCommitEnabled, kDeserializer, vDeserializer)

}
