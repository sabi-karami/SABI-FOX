package com.sabifox.vpn.platform.windows

import com.sabifox.vpn.platform.AppPaths
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Keeps one SABI-FOX window per Windows user. A second launch asks the running instance to
 * show its window (or to connect / disconnect) and exits.
 */
object SingleInstance {
    private val lockFile = File(AppPaths.runtime, "instance.lock")
    private val portFile = File(AppPaths.runtime, "instance.port")
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    fun tryAcquire(waitMillis: Long = 0L): Boolean {
        val deadline = System.currentTimeMillis() + waitMillis
        while (true) {
            val ch = RandomAccessFile(lockFile, "rw").channel
            val acquired = runCatching { ch.tryLock() }.getOrNull()
            if (acquired != null) {
                channel = ch
                lock = acquired
                return true
            }
            ch.close()
            if (System.currentTimeMillis() >= deadline) return false
            Thread.sleep(200)
        }
    }

    /** Listens for commands from later launches ("show", "connect", "disconnect"). */
    fun listen(onCommand: (String) -> Unit) {
        val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val socket = ServerSocket()
        socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        server = socket
        portFile.writeText("${socket.localPort}:$token")
        thread(isDaemon = true, name = "single-instance") {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching {
                    client.use {
                        it.soTimeout = 3_000
                        val line = it.getInputStream().bufferedReader().readLine().orEmpty()
                        val parts = line.split(' ', limit = 2)
                        if (parts.size == 2 && parts[0] == token) onCommand(parts[1].trim())
                    }
                }
            }
        }
    }

    fun signalRunningInstance(command: String): Boolean =
        runCatching {
                val (port, token) = portFile.readText().trim().split(':', limit = 2)
                Socket(InetAddress.getLoopbackAddress(), port.toInt()).use {
                    it.getOutputStream().write("$token $command\n".toByteArray())
                    it.getOutputStream().flush()
                }
                true
            }
            .getOrDefault(false)

    fun release() {
        runCatching { server?.close() }
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        server = null
        lock = null
        channel = null
    }
}
