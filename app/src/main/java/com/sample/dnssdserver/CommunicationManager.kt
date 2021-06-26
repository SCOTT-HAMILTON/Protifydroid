package com.sample.protifydroid

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlin.concurrent.thread

data class Client(val name: String, val processus: List<String>) {
}

class CommunicationManager(private val activity: MainActivity) {
    companion object {
        private const val TAG = "CommunicationManager"
        private const val SERVICE_TYPE = "_examplednssd._tcp"
        private const val SERVICE_PORT = 7755
    }

    fun getClientProcessus(index: Int) : List<String> {
        return serverRunnable.getClientProcessus(index)
    }
    fun getConnectedClient() : List<String> {
        return serverRunnable.getConnectedClient()
    }
    fun onClientsUpdate() {
        activity.onClientsUpdate()
    }
    fun onNewNotification(message: String) {
        activity.onNewNotification(message)
    }
    fun startServer() {
        thread {
            serverRunnable.run()
        }
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
        println("[log] CommunicationManager: On StopEverything")
        serverRunnable.stop()
        try {
            nsdManager.unregisterService(registrationListener)
        } catch (e: IllegalArgumentException) {}
    }
    private val serverRunnable: ServerRunnable by lazy {
        ServerRunnable(activity, this)
    }
    private var m_localPort: Int = 0
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
        // Create the NsdServiceInfo object, and populate it.
        val serviceInfo = NsdServiceInfo().apply {
            // The name is subject to change based on conflicts
            // with other services advertised on the same network.
            serviceName = "TestServerAndroid"
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
}
