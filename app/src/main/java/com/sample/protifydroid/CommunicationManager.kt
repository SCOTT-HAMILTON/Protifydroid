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
//    private var incomingHandler: IncomingHandler? = null
//    private var incomingHandlerLooper: Looper? = null
//    private var incomingHandlerThread: HandlerThread? = null
//    private var mMessenger: Messenger? = null
    private var mLocalPort: Int? = null
    private var mResolvedservicename: String = "Unknown"
    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            // Save the service name. Android may have changed it in order to
            // resolve a conflict, so update the name you initially requested
            // with the name Android actually used.
            mResolvedservicename = serviceInfo.serviceName
            dlog("Service registered: ${serviceInfo.serviceName}")
        }
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Registration failed! Put debugging code here to determine why.
            dlog("Failed to register service ${serviceInfo.serviceName}: $errorCode")
        }
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            // Service has been unregistered. This only happens when you call
            // NsdManager.unregisterService() and pass in this listener.
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
//    private var mServiceIBinder: IBinder? = null
    private var mIsBound = false
    private var connectedClientsRequesterTimer: Timer? = null
//    private var serviceBinderChecker: BinderChecker? = null
    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
//            if (ServerService.ServiceIBinder != null) {
//                ServerService.SingletoneService?.destroyMessenger()
//                ServerService.SingletoneService?.createMessenger()
//                mService = Messenger(ServerService.SingletoneService?.createIBinder())
            Messenger(ServerService.SingletoneService?.createIBinder()).let { messenger ->
//                serviceBinderChecker = BinderChecker(activity)
//                serviceBinderChecker?.let {
//                    messenger.?.linkToDeath(it, 0)
//                    dlog("Linked IBINDER to death tracker !")
//                }
                sendRegisterClient(messenger)
                mIsBound = true
                createConnectedClientsRequesterTimer()
                dlog("Connected to server service")
            }

//            } else {
//                dlog("Service IBinder is null, can't bind")
//            }
        }
        override fun onServiceDisconnected(className: ComponentName) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
//            mService = null
            // As part of the sample, tell the user what happened.
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
    private fun sendMessage(messenger: Messenger,
                            code: Int,
                            arg1: Int? = null,
                            setReplyTo: Boolean = false) {
        if (mIsBound) {
            CoroutineScope(Dispatchers.IO).launch {
//                Messenger(ServerService.SingletoneService?.createIBinder()).let { messenger ->
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
                        }
                        messenger.send(msg)
                    } catch (e: Exception) {
                        dlog("Failed to sendMessage: $e")
                    }
//                }
            }
        }
    }
    private fun sendBundleMessage(messenger: Messenger,
                                  code: Int,
                                  data: Bundle,
                                  setReplyTo: Boolean = false) {
        if (mIsBound) {
            CoroutineScope(Dispatchers.IO).launch {
//                Messenger(ServerService.ServiceIBinder).let { messenger ->
                    try {
                        val msg: Message = Message.obtain(
                            null, code
                        ).also {
                            it.data = data
                            if (setReplyTo) {
                                it.replyTo = createMessenger()
                            }
                        }
                        messenger.send(msg)
                        dlog("Bundle message sent ! ${messenger.binder.isBinderAlive}")
                    } catch (e: Exception) {
                        dlog("Failed to send bundle message: $e")
                    }
//                }
            }
        } else {
            dlog("LOL LOL Can't send message, not bound yet")
        }
    }
    fun askForClientProcessus(uuid: UUID, messenger: Messenger? = null) {
        dlog("LOL LOL LOL ASKING CLIENT PROCESSUS")
        val usedMessenger = messenger ?: Messenger(ServerService.SingletoneService?.createIBinder())
        sendBundleMessage(usedMessenger, ServerService.MSG_ASK_CLIENT_PROCESSUS,
            bundleOf(ServerService.CLIENT_UUID_BUNDLE_KEY to uuid), setReplyTo = true)
    }
    fun setOnClientProcessus(callback: (List<String>, UUID)->Unit) {
        onClientProcessusCallback = callback
    }
    private fun askForConnectedClients(messenger: Messenger) {
        sendMessage(messenger, ServerService.MSG_ASK_CONNECTED_CLIENTS, setReplyTo = true)
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
            connectedClientsRequesterTimer?.cancel()
            unbindFromServer()
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
//                incomingHandlerLooper?.quitSafely()
//                incomingHandlerThread?.quitSafely()
//            }
//            incomingHandlerThread?.join()
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
        // Create the NsdServiceInfo object, and populate it.
        val serviceInfo = NsdServiceInfo().apply {
            // The name is subject to change based on conflicts
            // with other services advertised on the same network.
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
                ServerService.MSG_NEW_NOTIFICATION ->
                    activity.onNewNotification(msg.data[ServerService.MESSAGE_BUNDLE_KEY] as String)
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
    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    /**
     * Class for interacting with the main interface of the service.
     */
    private fun sendRegisterClient(messenger: Messenger) {
        dlog("Sending MSG_REGISTER_CLIENT to service")
        val msg: Message = Message.obtain(
            null,
            ServerService.MSG_REGISTER_CLIENT
        ).apply {
            replyTo = createMessenger()
        }
//        if (mService == null) {
//            dlog("Error connecting to server service, service is null")
//        }
        runBlocking {
            CoroutineScope(Dispatchers.IO).launch {
//                Messenger(ServerService.ServiceIBinder).let { messenger ->
                    try {
                        messenger.send(msg)
                        askForClientProcessus(UUID.randomUUID(), messenger)
                    } catch (e: Exception) {
                        dlog("Failed to send MSG_REGISTER_CLIENT to service: $e")
                    }
//                }
            }
        }
    }
    private fun createConnectedClientsRequesterTimer() {
        connectedClientsRequesterTimer = timer(
            "CommManagerConnectedClientsRequester", initialDelay = 1000, period = 1000) {
            askForConnectedClients(Messenger(ServerService.SingletoneService?.createIBinder()))
        }
    }
    fun bindToServer() {

        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        dlog("BINDING TO SERVICE....")
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
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            CoroutineScope(Dispatchers.IO).launch {
                Messenger(ServerService.SingletoneService?.createIBinder()).let { messenger ->
                    try {
                        val msg: Message = Message.obtain(
                            null,
                            ServerService.MSG_UNREGISTER_CLIENT
                        ).apply {
                            replyTo = createMessenger()
                        }
                        messenger.send(msg)
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
