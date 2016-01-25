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

import scala.reflect.ClassTag
import scala.util.Try
import akka.actor.Actor
import akka.actor.Props
import kafka.producer.{ProducerConfig, KeyedMessage, Producer}

// only string so far
//TODO error handling
class KafkaPublisher[K,V : ClassTag](producer: Producer[K,V]) extends Actor {

  override def postStop(): Unit = Try(producer.close())

  def receive: Actor.Receive = {
    case e: Events.PublishTo[V] => publish(e)
  }

  private def publish(e: Events.PublishTo[V]): Unit =
    producer.send(e.data.toArray.map { new KeyedMessage[K,V](e.topic, _) }: _*)

}


object KafkaPublisher {

  def props(producerConfig: ProducerConfig): Props = {
    val producer = new Producer[String,String](producerConfig)
    Props(new KafkaPublisher(producer))
  }
}
