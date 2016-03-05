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
import java.util.concurrent.{CountDownLatch, Executors}

import scala.util.Try
import kafka.serializer.StringDecoder
import kafka.consumer.{ Consumer, ConsumerConfig }

/**
 * Very simple consumer of a single Kafka topic.
 * TODO this is the old consumer. Update to new consumer.
 */
class SimpleConsumer(
    val latch: CountDownLatch,
    consumerConfig: Map[String, String],
    topic: String,
    groupId: String,
    partitions: Int,
    numThreads: Int) {

  val connector = Consumer.create(createConsumerConfig)

  val streams = connector
    .createMessageStreams(Map(topic -> partitions), new StringDecoder(), new StringDecoder())
    .get(topic)

  val executor = Executors.newFixedThreadPool(numThreads)

  for (stream <- streams) {
    executor.submit(new Runnable() {
      def run(): Unit = {
        for (s <- stream) {
          while (s.iterator.hasNext) {
            latch.countDown()
          }
        }
      }
    })
  }

  private def createConsumerConfig: ConsumerConfig = {
    import scala.collection.JavaConverters._
    val props = new Properties()
    props.putAll(consumerConfig.asJava)
    new ConsumerConfig(props)
  }

  def shutdown(): Unit = Try {
    connector.shutdown()
    executor.shutdown()
  }
}
