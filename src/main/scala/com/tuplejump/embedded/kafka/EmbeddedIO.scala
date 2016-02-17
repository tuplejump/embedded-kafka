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

import java.io.{ File => JFile }

import org.apache.commons.io.FileUtils

object EmbeddedIO extends Logging {

  private val shutdownDeletePaths = new scala.collection.mutable.HashSet[String]()

  val logsDir = new JFile(".", "logs")
  dirSetup(new JFile(logsDir.getAbsolutePath))

  /** Creates tmp dirs that automatically get deleted on shutdown. */
  def createTempDir(tmpName: String): JFile =
    dirSetup(new JFile(logsDir, tmpName))

  private def dirSetup(dir: JFile): JFile = {
    if (logsDir.exists()) deleteRecursively(logsDir)
    dir.mkdir

    logger.info(s"Created dir ${dir.getAbsolutePath.replace("./", "")}")

    registerShutdownDeleteDir(dir)

    sys.runtime.addShutdownHook(new Thread("delete temp dir " + dir) {
      override def run(): Unit = {
        if (!hasRootAsShutdownDeleteDir(dir)) deleteRecursively(dir)
      }
    })
    dir
  }

  protected def registerShutdownDeleteDir(file: JFile) {
    shutdownDeletePaths.synchronized {
      shutdownDeletePaths += file.getAbsolutePath
    }
  }

  private def hasRootAsShutdownDeleteDir(file: JFile): Boolean = {
    val absolutePath = file.getAbsolutePath
    shutdownDeletePaths.synchronized {
      shutdownDeletePaths.exists { path =>
        !absolutePath.equals(path) && absolutePath.startsWith(path)
      }
    }
  }

  protected def deleteRecursively(delete: JFile): Unit =
    for {
      file <- Option(delete)
      if file.exists()
    } FileUtils.deleteDirectory(file)
}
