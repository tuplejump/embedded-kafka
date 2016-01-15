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

import java.util.concurrent.TimeoutException

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
 * Simple helper assertions.
 */
trait Assertions {

  /** Obtain current time (`System.nanoTime`) as Duration. */
  def now: FiniteDuration = System.nanoTime.nanos

  def eventually[T](timeout: Long, interval: Long)(func: => T): T = {
    def makeAttempt(): Either[Throwable, T] = {
      try Right(func) catch {
        case NonFatal(e) => Left(e)
      }
    }

    val startTime = System.currentTimeMillis()
    @tailrec
    def tryAgain(attempt: Int): T = {
      makeAttempt() match {
        case Right(result) => result
        case Left(e) =>
          val duration = System.currentTimeMillis() - startTime
          if (duration < timeout) {
            Thread.sleep(interval)
          } else {
            throw new TimeoutException(e.getMessage)
          }

          tryAgain(attempt + 1)
      }
    }

    tryAgain(1)
  }

}
