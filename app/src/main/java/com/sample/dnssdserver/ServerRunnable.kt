package com.sample.protifydroid

import android.util.Log
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class ServerRunnable(
        private val activity: MainActivity,
        private val communicationManager: CommunicationManager):
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
        activity.messageToDebugTextView("$TAG>$message")
    }
    inline fun getClientProcessus(index: Int) : List<String> {
        return clients[index].toClient().processus
    }
    inline fun getConnectedClient() : List<String> {
        return clients.map(ServerClientHandler::toClient).map{it.name}
    }
    fun onNewNotification(message: String) {
        communicationManager.onNewNotification(message)
    }
    fun onClientDisconnected(client: ServerClientHandler) {
        clients.remove(client)
        communicationManager.onClientsUpdate()
    }
    fun onClientUpdated() {
        communicationManager.onClientsUpdate()
    }
    fun stop() {
        println("[log] ServerRunnable: Stop")
        running.set(false)
        serverSocket.close()
    }
    override fun run() {
        running.set(true)
        val assignedPort = serverSocket.localPort
        communicationManager.onServerPortAssigned(assignedPort)
        while (running.get()) {
            val socket : Socket = try {
                serverSocket.accept()
            } catch (e: SocketException) {
                continue
            }
            dlog("Client connected: ${socket.inetAddress.hostAddress}")
            // Run client in it's own thread.
            val client = ServerClientHandler(activity, socket, this)
            clients += client
            Executors.newSingleThreadExecutor().execute {
                client.run()
            }
        }
        clients.forEach(ServerClientHandler::shutdown)
        println("[log] ServerRunnable: Finished running")
    }
}
