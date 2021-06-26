// Credits go to https://gist.github.com/Silverbaq/a14fe6b3ec57703e8cc1a63b59605876
package com.sample.protifydroid

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketException
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class ServerClientHandler(private val serverService: ServerService,
                          private val client: Socket,
                          private val serverRunnable: ServerRunnable) {
    companion object {
        private const val TAG = "ServerClientHandler";
    }
    private val reader: InputStream = client.getInputStream()
    private val writer: OutputStream = client.getOutputStream()
    private var running: AtomicBoolean = AtomicBoolean(true)
    private var dataClient = Client("Unknown", listOf())
    private var leftOver = ""
    fun toClient() : Client {
        return dataClient.copy()
    }
    private fun dlog(message: String) {
        reader
        Log.d(TAG, message)
        serverService.messageToDebugTextView("$TAG>$message")
    }
    private fun onNewMessage(message: String) : String {
        when {
            message.startsWith("name=") -> {
                dataClient = dataClient.copy(name = message.substringAfter("name="))
                serverRunnable.onClientUpdated()
                dlog("Name received : ${dataClient.name}")
                return ""
            }
            message.startsWith("processus=") -> {
                val processusStr = message.substringAfter("processus=")
                dataClient = dataClient.copy(processus = Gson().fromJson<List<String>>(processusStr,
                    object : TypeToken<List<String>>() {}.type))
                serverRunnable.onClientUpdated()
                dlog("List received : ${dataClient.processus}")
                return ""
            }
            message.startsWith("notif=") -> {
                val notifMessage = message.substringAfter("notif=")
                dlog("New Notif : $notifMessage")
                serverRunnable.onNewNotification(notifMessage)
                client.getInputStream().available()
                return ""
            }
            else -> return message
        }
    }
    private fun processReceivedText(received: String) {
        val newLeftOver =
            leftOver.plus(received)
            .split("\n")
                .map(::onNewMessage)
                .filter { it.isEmpty() }
                .joinToString("\n")
        leftOver = newLeftOver
        dlog("Leftover message : $leftOver")
    }
    fun run() {
        dlog("Started")
        running.set(true)
        // Welcome message
        while (running.get()) {
            if (!client.isConnected) {
                shutdown()
                running.set(false)
                break
            }
            val available = reader.available()
            if (available > 0) {
                dlog("available : $available")
                var buffer = ByteArray(available)
                reader.read(buffer, 0, available)
                var received = String(buffer, Charsets.UTF_8)
                dlog("received new message : `$received`")
                processReceivedText(received)
            } else {
                try {
                    if (reader.read() == -1) {
                        shutdown()
                        running.set(false)
                        break
                    } else {
                        Thread.sleep(1000)
                    }
                } catch  (e: SocketException){
                    running.set(false)
                    serverRunnable.onClientDisconnected(this)
                    break
                }
            }
        }
        println("[log] ServerClientHandler: finished !")
    }
    private fun writeln(message: String) {
        writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
    }
    fun shutdown() {
        running.set(false)
        client.close()
        serverRunnable.onClientDisconnected(this)
        dlog("${client.inetAddress.hostAddress} closed the connection")
    }
}
