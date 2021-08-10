package com.sample.protifydroid

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQ.sleep
import org.zeromq.ZMQException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class ClientInfo(val alive: Boolean, val name: String, val processus: List<String>, val uuid: String)

fun ClientInfo.toClient(): Client {
    return Client(name, processus, UUID.fromString(uuid))
}

class ServerRunnable(
    private val serverService: ServerService):
        Runnable
{
    companion object {
        private const val TAG = "ServerRunnable"
    }
    private var running: AtomicBoolean = AtomicBoolean(true)
    private val clients = ConcurrentHashMap<UUID, Client>()
    private val mJson = Json
    private var subscriber: ZMQ.Socket? = null
    private var zmqContext = ZContext(1)
    private fun dlog(message: String) {
        Log.d(TAG, message)
//        serverService.messageToDebugTextView("$TAG>$message")
    }
    private fun toast(message: String) {
        Log.d(TAG, message)
        serverService.toast("$TAG>$message")
    }
    fun getClient(uuid: UUID) : Client? {
        return clients[uuid]
    }
    fun getConnectedClients() : List<Client> {
        return clients.values.toList()
    }
    private fun onNewNotification(message: String) {
//        serverService.onNewNotification(message)
    }
    private fun onClientUpdated() {
//        serverService.onClientsUpdate()
    }
    fun stopEverything() {
        println("[log] ServerRunnable: Stop")
        running.set(false)
        subscriber?.close()
        zmqContext.close()
    }
    fun debugFakeLoop() {
        runBlocking {
            CoroutineScope(Dispatchers.IO).launch {
                while (running.get() && !Thread.currentThread().isInterrupted) {
                    toast("Server Runnable Still Running")
                    sleep(4)
                }
            }
        }
    }
    override fun run() {
        dlog("\n\n\t\tNEW SERVEUR RUNNABLE LAUNCHED\n")
        running.set(true)
//        debugFakeLoop()
//        return
        zmqContext.use { context ->
            //  Connect to weather server
            subscriber = context.createSocket(SocketType.SUB)
            if (subscriber == null) {
                dlog("Failed to initialize ZMQ subscriber socket, exitting")
                stopEverything()
                return
            }
            subscriber?.let { subscriber ->
                subscriber.receiveTimeOut = 10000
                subscriber.subscribe("")
                val assignedPort = subscriber.bindToRandomPort("tcp://*")
                serverService.onServerPortAssigned(assignedPort)
                while (running.get() && !Thread.currentThread().isInterrupted) {
//                    toast("waiting for reply....")
                    val received: ByteArray = try {
                        subscriber.recv(0)
                    } catch (e: ZMQException) {
                        running.set(false)
                        break
                    } ?: continue
                    val message = String(received)
                    toast(message)
                    val clientInfo = mJson.decodeFromString<ClientInfo>(message)
//                    dlog("Received client info: $clientInfo")
                    val realUuid = UUID.fromString(clientInfo.uuid)
                    if (clients.containsKey(realUuid)) {
                        if (clientInfo.alive) {
                            clients[realUuid] = clientInfo.toClient()
                            onClientUpdated()
                        } else {
                            clients.remove(realUuid)
                            onClientUpdated()
                        }
                    } else {
                        clients.putIfAbsent(realUuid, clientInfo.toClient())
                        onClientUpdated()
                    }
                }
            }
        }
        toast("[log] ServerRunnable: Finished running")
    }
}
