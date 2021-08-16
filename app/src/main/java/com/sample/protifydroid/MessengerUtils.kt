package com.sample.protifydroid

import android.os.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.Exception
import java.util.*

const val MSG_REGISTER_CLIENT = 1
const val MSG_SCOPE_UNUSED = -10
const val MSG_UNREGISTER_CLIENT = 2
const val MSG_PORT_ASSIGNED = 3
const val MSG_DEBUG_MESSAGE = 4
const val MSG_ASK_ALL_UPDATE = 6
const val MSG_ASK_CLIENT_PROCESSUS = 7
const val MSG_CLIENT_PROCESSUS_ANWSER = 8
const val MSG_ASK_CONNECTED_CLIENTS = 9
const val MSG_CONNECTED_CLIENTS_ANWSER = 10

const val PROCESSUS_BUNDLE_KEY = "processus"
const val CLIENT_UUID_BUNDLE_KEY = "clientUuid"
const val SCOPE_UUID_BUNDLE_KEY = "scopeUuid"
const val CONNECTED_CLIENTS_BUNDLE_KEY = "connectedClients"
const val MESSAGE_BUNDLE_KEY = "message"

data class HandlerScope(val handler: Handler,
                        val looper: Looper,
                        val thread: HandlerThread
)
data class MessengerScope(val messenger: Messenger, val handlerScope: HandlerScope, val uuid: UUID) {
    fun destroy() {
        handlerScope.run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                looper.quitSafely()
                thread.quitSafely()
            }
        }
    }
}
fun withServerMessengerScope(body: (messengerScope: MessengerScope)->Unit) {
    ServerService.SingletoneService?.createMessengerScope()?.let { scope ->
        body(scope)
    }
}
fun createMessenger(makeHandler: (looper: Looper)->Handler): Messenger {
    HandlerThread(
        UUID.randomUUID().toString(),
        Process.THREAD_PRIORITY_BACKGROUND).apply {
        start()
        return Messenger(makeHandler(looper))
    }
}
fun Message.setLastMessage(messengerScopeUuid: UUID): Message {
    arg2 = MSG_SCOPE_UNUSED
    data.putSerializable(SCOPE_UUID_BUNDLE_KEY, messengerScopeUuid)
    return this
}
interface IncomingHandlerReceiver: LogReceiver {
    fun onServerPortAssigned(port: Int)
    fun messageToDebugTextViewAsync(message: String)
    fun makeIncomingHandler(looper: Looper): IncomingHandler
    var onClientProcessusCallback: ((List<String>, UUID)->Unit)?
    var onConnectedClientsCallback:  ((List<Client>)->Unit)?
}
class IncomingHandler(private val receiver: IncomingHandlerReceiver, looper: Looper) : Handler(looper) {
    override fun handleMessage(msg: Message) {
        when (msg.what) {
            MSG_PORT_ASSIGNED -> receiver.onServerPortAssigned(msg.arg1)
            MSG_DEBUG_MESSAGE ->
                receiver.messageToDebugTextViewAsync(msg.data[MESSAGE_BUNDLE_KEY] as String)
            MSG_CLIENT_PROCESSUS_ANWSER -> {
                val processusList = (msg.data[PROCESSUS_BUNDLE_KEY] as? List<*>)
                val uuid = msg.data[CLIENT_UUID_BUNDLE_KEY] as? UUID
                val processus = processusList?.filterIsInstance<String>()
                if (processus != null && processus.size == processusList.size && uuid != null ) {
                    receiver.onClientProcessusCallback?.invoke(processus, uuid)
                } else {
                    receiver.dlog("Received client processus answer but data is invalid: $processusList, $uuid")
                }
            }
            MSG_CONNECTED_CLIENTS_ANWSER -> {
                val clientsList = msg.data[CONNECTED_CLIENTS_BUNDLE_KEY] as? List<*>
                val list = clientsList?.filterIsInstance<Client>()
                if (list != null && list.size == clientsList.size) {
                    receiver.onConnectedClientsCallback?.invoke(list)
                } else {
                    receiver.dlog("Received connected clients answer but data is invalid: $clientsList")
                }
            }
            else -> super.handleMessage(msg)
        }
    }
}
fun CommunicationManager.sendBundleMessage(messengerScope: MessengerScope,
                      code: Int,
                      data: Bundle,
                      setReplyTo: Boolean = false,
                      setIsLastMessage: Boolean = false) {
    if (isBound) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val msg: Message = Message.obtain(
                    null, code
                ).also { messenger ->
                    messenger.data = data
                    if (setReplyTo) {
                        messenger.replyTo = createMessenger { makeIncomingHandler(it) }
                    }
                    if (setIsLastMessage) {
                        messenger.setLastMessage(messengerScope.uuid)
                    }
                }
                messengerScope.messenger.send(msg)
            } catch (e: Exception) {
                dlog("Failed to send bundle message: $e")
            }
        }
    } else {
        dlog("Can't send message, not bound yet")
    }
}
fun CommunicationManager.sendMessage(messengerScope: MessengerScope,
                        code: Int,
                        arg1: Int? = null,
                        setReplyTo: Boolean = false,
                        setIsLastMessage: Boolean = false) {
    if (isBound) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val msg: Message = when (arg1) {
                    null -> Message.obtain(
                        null,
                        code
                    )
                    else -> Message.obtain(
                        null,
                        code, arg1, 0, null
                    )
                }.apply {
                    if (setReplyTo) {
                        replyTo = createMessenger { makeIncomingHandler(it) }
                    }
                    if (setIsLastMessage) {
                        setLastMessage(messengerScope.uuid)
                    }
                }
                messengerScope.messenger.send(msg)
            } catch (e: Exception) {
                dlog("Failed to sendMessage: $e")
            }
        }
    }
}
fun CommunicationManager.sendRegisterClientImpl(messengerScope: MessengerScope,
                                setIsLastMessage: Boolean
) {
    dlog("Sending MSG_REGISTER_CLIENT to service")
    val msg: Message = Message.obtain(
        null,
        MSG_REGISTER_CLIENT
    ).apply {
        replyTo = createMessenger { makeIncomingHandler(it) }
        if (setIsLastMessage) {
            setLastMessage(messengerScope.uuid)
        }
    }
    runBlocking {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                messengerScope.messenger.send(msg)
                askForClientProcessus(UUID.randomUUID(), messengerScope)
            } catch (e: Exception) {
                dlog("Failed to send MSG_REGISTER_CLIENT to service: $e")
            }
        }
    }
}