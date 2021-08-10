package com.sample.protifydroid

import android.content.ComponentName
import android.content.Context.*
import android.content.Intent
import android.content.ServiceConnection
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.os.bundleOf
import kotlinx.coroutines.*
import java.lang.Exception
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.timer

data class Client(val name: String, val processus: List<String>, val uuid: UUID)

class CommunicationManager(private val activity: MainActivity) {
    companion object {
        private const val TAG = "CommunicationManager"
        private const val SERVICE_TYPE = "_examplednssd._tcp"
        private const val SERVICE_PORT = 7755
    }
    private var stoppedEverything = AtomicBoolean(false)
    private var onClientProcessusCallback: ((List<String>, UUID)->Unit)? = null
    private var onConnectedClients: ((List<Client>)->Unit)? = null
    private var mLocalPort: Int? = null
    private var mResolvedservicename: String = "Unknown"
    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            mResolvedservicename = serviceInfo.serviceName
            dlog("Service registered: ${serviceInfo.serviceName}")
        }
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            dlog("Failed to register service ${serviceInfo.serviceName}: $errorCode")
        }
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            dlog("Service unregistered: ${serviceInfo.serviceName}")
        }
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Unregistration failed. Put debugging code here to determine why.
            dlog("Failed to unregister service ${serviceInfo.serviceName}: $errorCode")
        }
    }
    private val nsdManager : NsdManager by lazy {
        (activity.getSystemService(NSD_SERVICE) as NsdManager)
    }
    private var mIsBound = false
    private var allUpdatesRequester: Timer? = null
    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            withServerMessengerScope { messengerScope ->
                sendRegisterClient(messengerScope)
            }
            mIsBound = true
            createAllUpdatesRequester()
            dlog("Connected to server service")
        }
        override fun onServiceDisconnected(className: ComponentName) {
            Toast.makeText(
                activity, "Server Service disconnected",
                Toast.LENGTH_SHORT
            ).show()
        }
        override fun onBindingDied(name: ComponentName?) {
            dlog("Binding died on component: $name")
            super.onBindingDied(name)
        }
        override fun onNullBinding(name: ComponentName?) {
            dlog("Null binding on component: $name")
            super.onNullBinding(name)
        }
    }
    fun withServerMessengerScope(body: (messengerScope: MessengerScope)->Unit) {
        val scope = ServerService.SingletoneService?.createMessengerScope()
        scope?.let { scope ->
            body(scope)
        }
    }
    fun startServerService() {
        activity.startService(Intent(activity, ServerService::class.java).apply{
            putExtra(ServerService.STOP_SERVICE_INTENT_EXTRA_KEY, false)
        })
    }
    private fun createMessenger(): Messenger {
        HandlerThread(UUID.randomUUID().toString(),
            Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            return Messenger(IncomingHandler(looper))
        }
    }
    private fun sendMessage(messengerScope: MessengerScope,
                            code: Int,
                            arg1: Int? = null,
                            setReplyTo: Boolean = false,
                            setIsLastMessage: Boolean = false) {
        if (mIsBound) {
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
                            replyTo = createMessenger()
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
    private fun sendBundleMessage(messengerScope: MessengerScope,
                                  code: Int,
                                  data: Bundle,
                                  setReplyTo: Boolean = false,
                                  setIsLastMessage: Boolean = false) {
        if (mIsBound) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val msg: Message = Message.obtain(
                        null, code
                    ).also {
                        it.data = data
                        if (setReplyTo) {
                            it.replyTo = createMessenger()
                        }
                        if (setIsLastMessage) {
                            it.setLastMessage(messengerScope.uuid)
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

    private fun askForAllUpdates(messengerScope: MessengerScope, setIsLastMessage: Boolean = false) {
        sendMessage(messengerScope, ServerService.MSG_ASK_ALL_UPDATE,
            setReplyTo = true, setIsLastMessage = setIsLastMessage)
    }
    private fun askForClientProcessus(uuid: UUID, messengerScope: MessengerScope, setIsLastMessage: Boolean = false) {
        sendBundleMessage(messengerScope, ServerService.MSG_ASK_CLIENT_PROCESSUS,
            bundleOf(ServerService.CLIENT_UUID_BUNDLE_KEY to uuid), setReplyTo = true,
            setIsLastMessage = setIsLastMessage)
    }
    fun askForClientProcessus(uuid: UUID, setIsLastMessage: Boolean = false) {
        withServerMessengerScope { messengerScope ->
            askForClientProcessus(uuid, messengerScope, setIsLastMessage = setIsLastMessage)
        }
    }
    fun setOnClientProcessus(callback: (List<String>, UUID)->Unit) {
        onClientProcessusCallback = callback
    }

    fun setOnConnectedClients(callback: (List<Client>)->Unit) {
        onConnectedClients = callback
    }
    fun pause() {
        try {
            nsdManager.unregisterService(registrationListener)
        } catch (e: IllegalArgumentException) {}
    }
    fun resume() {
        registerService()
    }
    fun stopEverything() {
        runBlocking {
            launch {
                try {
                    nsdManager.unregisterService(registrationListener)
                } catch (e: IllegalArgumentException) {}
            }
            allUpdatesRequester?.cancel()
            unbindFromServer()
            stoppedEverything.set(true)
        }
    }
    private fun dlog(message: String) {
        Log.d(TAG, message)
        activity.messageToDebugTextViewAsync("$TAG>$message")
    }
    private fun registerService() {
        if (stoppedEverything.get()) {
            return
        }
        if (mLocalPort == null) {
            return
        }
        dlog("Registering service with local port : $mLocalPort")
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "ProtifyAndroidServer"
            serviceType = SERVICE_TYPE
            port = SERVICE_PORT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute(activity.getString(R.string.SERVER_PORT_ZERO_CONF_KEY),
                    mLocalPort.toString())
            }
        }
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: IllegalArgumentException) { }
    }
    fun onServerPortAssigned(port: Int) {
        mLocalPort = port
        dlog("Local port is $port")
        registerService()
    }
    ///// Binder
    internal inner class IncomingHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                ServerService.MSG_PORT_ASSIGNED -> onServerPortAssigned(msg.arg1)
                ServerService.MSG_DEBUG_MESSAGE ->
                    activity.messageToDebugTextViewAsync(msg.data[ServerService.MESSAGE_BUNDLE_KEY] as String)
                ServerService.MSG_CLIENT_PROCESSUS_ANWSER -> {
                    val processusList = (msg.data[ServerService.PROCESSUS_BUNDLE_KEY] as? List<*>)
                    val uuid = msg.data[ServerService.CLIENT_UUID_BUNDLE_KEY] as? UUID
                    val processus = processusList?.filterIsInstance<String>()
                    if (processus != null && processus.size == processusList.size && uuid != null ) {
                        onClientProcessusCallback?.invoke(processus, uuid)
                    } else {
                        dlog("Received client processus answer but data is invalid: $processusList, $uuid")
                    }
                }
                ServerService.MSG_CONNECTED_CLIENTS_ANWSER -> {
                    val clientsList = msg.data[ServerService.CONNECTED_CLIENTS_BUNDLE_KEY] as? List<*>
                    val list = clientsList?.filterIsInstance<Client>()
                    if (list != null && list.size == clientsList.size) {
                        onConnectedClients?.invoke(list)
                    } else {
                        dlog("Received connected clients answer but data is invalid: $clientsList")
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }
    private fun sendRegisterClient(messengerScope: MessengerScope,
                                   setIsLastMessage: Boolean = false) {
        dlog("Sending MSG_REGISTER_CLIENT to service")
        val msg: Message = Message.obtain(
            null,
            ServerService.MSG_REGISTER_CLIENT
        ).apply {
            replyTo = createMessenger()
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
    private fun createAllUpdatesRequester() {
        allUpdatesRequester = timer(
            "CommManagerAllUpdatesRequester", initialDelay = 1000, period = 1000) {
            withServerMessengerScope { messengerScope ->
                askForAllUpdates(messengerScope, setIsLastMessage = true)
            }
        }
    }
    fun bindToServer() {
        dlog("Binding to service...")
        try {
            activity.bindService(
                Intent(
                    activity,
                    ServerService::class.java,
                ),
                mConnection, BIND_ABOVE_CLIENT or BIND_AUTO_CREATE or BIND_DEBUG_UNBIND,
            )
        } catch (e: SecurityException) {
            dlog("Security Exception when binding to service")
        }
    }
    private fun unbindFromServer() {
        if (mIsBound) {
            CoroutineScope(Dispatchers.IO).launch {
                withServerMessengerScope { messengerScope ->
                    try {
                        val msg: Message = Message.obtain(
                            null,
                            ServerService.MSG_UNREGISTER_CLIENT
                        ).apply {
                            replyTo = createMessenger()
                            setLastMessage(messengerScope.uuid)
                        }
                        messengerScope.messenger.send(msg)
                    } catch (e: Exception) {
                        dlog("Failed to send unbind message: $e")
                    }
                }
            }
            // Detach our existing connection.
            activity.unbindService(mConnection)
            mIsBound = false
        }
    }
}
