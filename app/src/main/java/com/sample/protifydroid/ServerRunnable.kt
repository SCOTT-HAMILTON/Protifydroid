package com.sample.protifydroid

import android.os.CountDownTimer
import android.util.Log
import com.conversantmedia.util.concurrent.ConcurrentStack
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
import kotlin.concurrent.timer

@Serializable
data class ClientInfo(val alive: Boolean, val name: String, val processus: List<String>, val uuid: String)

data class ClientExtra(val client: Client, val deathTimer: Timer)

fun ClientInfo.toClient(): Client {
    return Client(name, processus, UUID.fromString(uuid))
}

class ServerRunnable(
    private val serverService: ServerService):
        Runnable
{
    companion object {
        private const val TAG = "ServerRunnable"
        private const val NOTIF_DIED_DELIMITER = "notifdied="
    }
    private var running: AtomicBoolean = AtomicBoolean(true)
    private val clients = ConcurrentHashMap<UUID, ClientExtra>()
    private val notifications = ConcurrentStack<String>(0)
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
        return clients[uuid]?.client
    }
    fun getConnectedClients() : List<Client> {
        return clients.values.toList().map { it.client }
    }
    fun getNotifications(): List<String> {
        val list = mutableListOf<String>()
        while (notifications.size() > 0) {
            list += notifications.pop()
        }
        return list.toList()
    }
    private fun onNewNotification() {
        serverService.onNewNotifications()
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
    private fun createDeathTimerForClient(clientUuid: UUID): Timer {
        return timer("deathTimerForClient$clientUuid",
            initialDelay = 4000,
            period = 100000) {
            clients.remove(clientUuid)
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
                    if (message.startsWith(NOTIF_DIED_DELIMITER)) {
                        val diedProcess = message.substringAfter(NOTIF_DIED_DELIMITER)
                        toast("Process $diedProcess died")
                        notifications.push(diedProcess)
                        onNewNotification()
                    }
                    else {
                        val clientInfo = mJson.decodeFromString<ClientInfo>(message)
                        val realUuid = UUID.fromString(clientInfo.uuid)
                        if (clients.containsKey(realUuid)) {
                            if (clientInfo.alive) {
                                val deathTimer = clients[realUuid]?.deathTimer
                                deathTimer?.cancel()
                                val newDeathTimer = createDeathTimerForClient(realUuid)
                                clients[realUuid] =
                                    ClientExtra(clientInfo.toClient(), newDeathTimer)
                            } else {
                                clients.remove(realUuid)
                            }
                        } else {
                            val newDeathTimer = createDeathTimerForClient(realUuid)
                            clients.putIfAbsent(
                                realUuid,
                                ClientExtra(clientInfo.toClient(), newDeathTimer)
                            )
                        }
                    }
                }
            }
        }
        toast("[log] ServerRunnable: Finished running")
    }
}
