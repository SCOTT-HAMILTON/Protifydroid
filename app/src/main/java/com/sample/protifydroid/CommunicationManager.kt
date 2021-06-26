package com.sample.protifydroid

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.*
import android.util.Log
import android.widget.Toast

data class Client(val name: String, val processus: List<String>) {
}

class CommunicationManager(private val activity: MainActivity) {
    companion object {
        private const val TAG = "CommunicationManager"
        private const val SERVICE_TYPE = "_examplednssd._tcp"
        private const val SERVICE_PORT = 7755
    }
    private var stoppedEverything = false
    private var onClientProcessusCallback: ((List<String>, String)->Unit)? = null
    private var onConnectedClients: ((List<String>)->Unit)? = null
    fun startServerService() {
        activity.startService(Intent(activity, ServerService::class.java).apply{
            putExtra(ServerService.STOP_SERVICE_INTENT_EXTRA_KEY, false)
        })
    }
    fun sendMessage(code: Int, arg1: Int? = null) {
        if (mIsBound) {
            mService?.also { messenger ->
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
                    }
                    messenger.send(msg)
                } catch (e: RemoteException) {
                }
            }
        }
    }
    fun askForClientProcessus(index: Int) {
        sendMessage(ServerService.MSG_ASK_CLIENT_PROCESSUS, index)
    }
    fun setOnClientProcessus(callback: (List<String>, String)->Unit) {
        onClientProcessusCallback = callback
    }
    fun askForConnectedClients() {
        sendMessage(ServerService.MSG_ASK_CONNECTED_CLIENTS)
    }
    fun setOnConnectedClients(callback: (List<String>)->Unit) {
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
        stoppedEverything = true
        try {
            nsdManager.unregisterService(registrationListener)
        } catch (e: IllegalArgumentException) {}
    }
    private var m_localPort: Int? = null
    private var m_resolvedServiceName: String = "Unknown"
    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            // Save the service name. Android may have changed it in order to
            // resolve a conflict, so update the name you initially requested
            // with the name Android actually used.
            m_resolvedServiceName = serviceInfo.serviceName
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
    private fun dlog(message: String) {
        Log.d(TAG, message)
        activity.messageToDebugTextView("$TAG>$message")
    }
    private val nsdManager : NsdManager by lazy {
        (activity.getSystemService(Context.NSD_SERVICE) as NsdManager)
    }
    private fun registerService() {
        if (stoppedEverything) {
            return
        }
        if (m_localPort == null) {
            return
        }
        dlog("Registering service with local port : $m_localPort")
        // Create the NsdServiceInfo object, and populate it.
        val serviceInfo = NsdServiceInfo().apply {
            // The name is subject to change based on conflicts
            // with other services advertised on the same network.
            serviceName = "ProtifyAndroidServer"
            serviceType = SERVICE_TYPE
            port = SERVICE_PORT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("SERVER_PORT", m_localPort.toString())
            }
        }
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: IllegalArgumentException) { }
    }
    fun onServerPortAssigned(port: Int) {
        m_localPort = port
        dlog("Local port is $port")
        registerService()
    }
    ///// Binder
    var mService: Messenger? = null
    var mIsBound = false
    internal inner class IncomingHandler : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                ServerService.MSG_PORT_ASSIGNED -> onServerPortAssigned(msg.arg1)
                ServerService.MSG_DEBUG_MESSAGE ->
                    activity.messageToDebugTextView(msg.data["message"] as String)
                ServerService.MSG_NEW_NOTIFICATION ->
                    activity.onNewNotification(msg.data["message"] as String)
                ServerService.MSG_CLIENTS_UPDATE -> {
                    dlog("Clients Update")
                    activity.onClientsUpdate()
                }
                ServerService.MSG_CLIENT_PROCESSUS_ANWSER -> {
                    onClientProcessusCallback?.invoke(
                        msg.data[ServerService.PROCESSUS_BUNDLE_KEY] as List<String>,
                        msg.data[ServerService.CLIENT_NAME_BUNDLE_KEY] as String)
                }
                ServerService.MSG_CONNECTED_CLIENTS_ANWSER -> {
                    dlog("Received connected clients answer : ${msg.data[ServerService.CONNECTED_CLIENTS_BUNDLE_KEY] as List<String>}")
                    if (onConnectedClients == null) {
                        dlog("But callback for onConnectedClients is null")
                    } else {
                        dlog("And callback for onConnectedClients isn't null !!!")

                    }
                    onConnectedClients?.invoke(
                        msg.data[ServerService.CONNECTED_CLIENTS_BUNDLE_KEY] as List<String>
                    )
                }
                else -> super.handleMessage(msg)
            }
        }
    }
    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    val mMessenger: Messenger = Messenger(IncomingHandler())
    /**
     * Class for interacting with the main interface of the service.
     */
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
            mService = Messenger(service)
            mService?.also { messenger ->
                try {
                    var msg: Message = Message.obtain(
                        null,
                        ServerService.MSG_REGISTER_CLIENT
                    )
                    msg.replyTo = mMessenger
                    messenger.send(msg)
                } catch (e: RemoteException) {
                }
            }
            Toast.makeText(
                activity, "Server Service connected",
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null
            // As part of the sample, tell the user what happened.
            Toast.makeText(
                activity, "Server Service disconnected",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    fun bindToServer() {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        activity.bindService(
            Intent(
                activity,
                ServerService::class.java
            ), mConnection, BIND_AUTO_CREATE
        )
        mIsBound = true
    }
    fun unbindFromServer() {
        if (mIsBound) {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            mService?.also { messenger ->
                try {
                    val msg: Message = Message.obtain(
                        null,
                        ServerService.MSG_UNREGISTER_CLIENT
                    )
                    msg.replyTo = mMessenger
                    messenger.send(msg)
                } catch (e: RemoteException) {
                }
            }
            // Detach our existing connection.
            activity.unbindService(mConnection)
            mIsBound = false
        }
    }
}
