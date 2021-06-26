package com.sample.protifydroid

import android.util.Log
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class ServerRunnable(
        private val serverService: ServerService):
        Runnable
{
    companion object {
        private const val TAG = "ServerRunnable";
    }
    private var running: AtomicBoolean = AtomicBoolean(true)
    private val serverSocket : ServerSocket by lazy {
        ServerSocket(0)
    }
    val clients = mutableListOf<ServerClientHandler>()
    private fun dlog(message: String) {
        Log.d(TAG, message)
        serverService.messageToDebugTextView("$TAG>$message")
    }
    inline fun getClientProcessus(index: Int) : List<String>? {
        return if (index >= 0 && index < clients.size) {
            clients[index].toClient().processus
        } else {
            null
        }
    }
    inline fun clientExists(name: String) : Boolean {
        return clients.map{it.toClient().name}.contains(name)
    }
    inline fun getConnectedClient() : List<String> {
        return clients.map(ServerClientHandler::toClient).map{it.name}
    }
    fun onNewNotification(message: String) {
        serverService.onNewNotification(message)
    }
    fun onClientDisconnected(client: ServerClientHandler) {
        clients.remove(client)
        serverService.onClientsUpdate()
    }
    fun onClientUpdated() {
        serverService.onClientsUpdate()
    }
    fun stop() {
        println("[log] ServerRunnable: Stop")
        running.set(false)
        serverSocket.close()
    }
    override fun run() {
        running.set(true)
        val assignedPort = serverSocket.localPort
        serverService.onServerPortAssigned(assignedPort)
        while (running.get()) {
            val socket : Socket = try {
                serverSocket.accept()
            } catch (e: SocketException) {
                continue
            }
            dlog("Client connected: ${socket.inetAddress.hostAddress}")
            // Run client in it's own thread.
            val client = ServerClientHandler(serverService, socket, this)
            clients += client
            Executors.newSingleThreadExecutor().execute {
                client.run()
            }
        }
        clients.forEach(ServerClientHandler::shutdown)
        println("[log] ServerRunnable: Finished running")
    }
}
