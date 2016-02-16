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

import java.io.{ File => JFile, IOException }
import java.nio.file.{ Paths, Path }
import java.util.UUID

import scala.util.Try

trait EmbeddedIO {

  private val shutdownDeletePaths = new scala.collection.mutable.HashSet[String]()

  /** Creates tmp dirs that automatically get deleted on shutdown. */
  def createTempDir(tmpName: String = UUID.randomUUID.toString): JFile = {
    val tmp = Paths.get(sys.props("java.io.tmpdir"))
    //tmp.getFileSystem.getPath(tmpName).toAbsolutePath
    val name: Path = tmp.getFileSystem.getPath(tmpName)
    if (Option(name.getParent).isDefined) throw new IllegalArgumentException("Invalid prefix or suffix")
    val dir = new JFile(tmp.resolve(name).toString)
    registerShutdownDeleteDir(dir)

    sys.runtime.addShutdownHook(new Thread("delete embedded server temp dir " + dir) {
      override def run() {
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

  /** For now: raises an error if deletion failed. TODO validation type. */
  protected def deleteRecursively(file: JFile): Unit =
    for (file <- Option(file)) {
      if (file.isDirectory && !isSymlink(file)) {
        for (child <- file.listFiles) Try(deleteRecursively(child))
      }
      if (!file.delete && file.exists) {
        throw new IOException("Failed to delete: " + file.getAbsolutePath)
      }
    }

  private def isSymlink(file: JFile): Boolean = {
    if (Option(file).isEmpty || scala.util.Properties.isWin) false
    else {
      val fcd = if (Option(file.getParent).isEmpty) file
      else new JFile(file.getParentFile.getCanonicalFile, file.getName)
      if (fcd.getCanonicalFile.equals(fcd.getAbsoluteFile)) false else true
    }
  }
}
