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

object Events {
  sealed trait Task extends Serializable
  case object PublishTask extends Task

  sealed trait Bufferable[A] extends Serializable {
    def data: Iterable[A]
  }
  final case class SampleEvent(data: AnyRef*)
  final case class PublishTo[A](topic: String, data: A*) extends Bufferable[A]
}
