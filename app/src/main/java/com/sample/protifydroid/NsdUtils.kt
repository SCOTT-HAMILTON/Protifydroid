package com.sample.protifydroid

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build

private const val SERVICE_TYPE = "_examplednssd._tcp"
private const val SERVICE_PORT = 7755
private const val SERVER_PORT_ZERO_CONF_KEY = "SRV_PORT"

interface NsdRegistrationReceiver: LogReceiver {
    var localServerPort: Int?
    var resolvedServiceName: String
    val registrationListener: NsdRegistrationListener
    val nsdManager: NsdManager
    fun isEverythingStopped(): Boolean
}

class NsdRegistrationListener(private val receiver: NsdRegistrationReceiver) :
    NsdManager.RegistrationListener {
    override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
        receiver.resolvedServiceName = serviceInfo.serviceName
        receiver.dlog("Service registered: ${serviceInfo.serviceName}")
    }
    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
        receiver.dlog("Failed to register service ${serviceInfo.serviceName}: $errorCode")
    }
    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
        receiver.dlog("Service unregistered: ${serviceInfo.serviceName}")
    }
    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
        // Unregistration failed. Put debugging code here to determine why.
        receiver.dlog("Failed to unregister service ${serviceInfo.serviceName}: $errorCode")
    }
}
fun NsdRegistrationReceiver.registerService() {
    if (isEverythingStopped()) {
        return
    }
    val localPort = localServerPort ?: return
    dlog("Registering service with local port : $localPort")
    val serviceInfo = NsdServiceInfo().apply {
        serviceName = "ProtifyAndroidServer"
        serviceType = SERVICE_TYPE
        port = SERVICE_PORT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setAttribute(SERVER_PORT_ZERO_CONF_KEY, localPort.toString())
        }
    }
    try {
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    } catch (e: IllegalArgumentException) { }
}