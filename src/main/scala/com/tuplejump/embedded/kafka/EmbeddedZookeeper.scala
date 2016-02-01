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

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

import scala.util.Try
import org.I0Itec.zkclient.exception.ZkMarshallingError
import org.I0Itec.zkclient.serialize.ZkSerializer
import org.apache.zookeeper.server.{ NIOServerCnxnFactory, ZooKeeperServer }

/**
 * Implements a simple standalone ZooKeeperServer.
 * To create a ZooKeeper client object, the application needs to pass a
 * connection string containing a comma separated list of host:port pairs,
 * each corresponding to a ZooKeeper server.
 * <p>
 * Session establishment is asynchronous. This constructor will initiate
 * connection to the server and return immediately - potentially (usually)
 * before the session is fully established. The watcher argument specifies
 * the watcher that will be notified of any changes in state. This
 * notification can come at any point before or after the constructor call
 * has returned.
 * <p>
 * The instantiated ZooKeeper client object will pick an arbitrary server
 * from the connectString and attempt to connect to it. If establishment of
 * the connection fails, another server in the connect string will be tried
 * (the order is non-deterministic, as we random shuffle the list), until a
 * connection is established. The client will continue attempts until the
 * session is explicitly closed.
 */
class EmbeddedZookeeper(val connectTo: String, val tickTime: Int) extends EmbeddedIO with Assertions with Logging {

  /** Should an error occur, make sure it shuts down. */

  val snapshotDir = createTempDir("zk-test-snapshots")

  val logDir = createTempDir("zk-test-logs")

  logger.info("Starting Zookeeper")

  private val _factory = new AtomicReference[Option[NIOServerCnxnFactory]](None)

  private val _zookeeper = new AtomicReference[Option[ZooKeeperServer]](None)

  def isRunning: Boolean = _zookeeper.get exists (_.isRunning)

  def zookeeper: ZooKeeperServer = _zookeeper.get.getOrElse {
    logger.info("Attempt to call server before starting EmbeddedKafka instance. Starting automatically...")
    start()
    eventually(5000, 500)(assert(isRunning))

    _zookeeper.get.getOrElse(throw new IllegalStateException("Kafka server not initialized."))
  }

  /** Starts only one. */
  def start(): Unit = {
    val server = new ZooKeeperServer(snapshotDir, logDir, tickTime)
    _zookeeper.set(Some(server))

    val (ip, port) = {
      val splits = connectTo.split(":")
      (splits(0), splits(1).toInt)
    }

    val f = new NIOServerCnxnFactory()
    f.configure(new InetSocketAddress(ip, port), 16)
    f.startup(server)

    _factory.set(Some(f))

    logger.info(s"ZooKeeperServer isRunning: $isRunning")
  }

  def shutdown(): Unit = {
    logger.info(s"Shutting down ZK NIOServerCnxnFactory.")

    for (v <- _factory.get) v.shutdown()
    _factory.set(None)

    for (v <- _zookeeper.get) {
      Try(v.shutdown())
      //awaitCond(!v.isRunning, 2000.millis)
      logger.info(s"ZooKeeper server shut down.")
    }
    _zookeeper.set(None)

    Try {
      deleteRecursively(snapshotDir)
      deleteRecursively(logDir)
    }
  }
}

object DefaultStringSerializer extends ZkSerializer {

  @throws(classOf[ZkMarshallingError])
  def serialize(data: Object): Array[Byte] = data match {
    case a: String => a.getBytes("UTF-8")
    case _         => throw new ZkMarshallingError(s"Unsupported type '${data.getClass}'")
  }

  @throws(classOf[ZkMarshallingError])
  def deserialize(bytes: Array[Byte]): Object = bytes match {
    case b if Option(b).isEmpty => "" //ick
    case b                      => new String(bytes, "UTF-8")
  }
}
