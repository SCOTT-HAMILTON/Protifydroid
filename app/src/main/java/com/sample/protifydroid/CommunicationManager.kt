package com.sample.protifydroid

import android.content.Context.*
import android.content.Intent
import android.net.nsd.NsdManager
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.os.bundleOf
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.timer

data class Client(val name: String, val processus: List<String>, val uuid: UUID)

class CommunicationManager(private val activity: MainActivity,
                           override var isBound: Boolean = false,
                           override var localServerPort: Int? = null,
                           override var resolvedServiceName: String = "",
                           override var onClientProcessusCallback: ((List<String>, UUID) -> Unit)? = null,
                           override var onConnectedClientsCallback: ((List<Client>) -> Unit)? = null
):
    IncomingHandlerReceiver,
    ServiceConnectionReceiver,
    NsdRegistrationReceiver, ServerBinder(activity) {
    companion object {
        private const val TAG = "CommunicationManager"
    }
    private var stoppedEverything = AtomicBoolean(false)
    private var allUpdatesRequester: Timer? = null
    override val registrationListener = NsdRegistrationListener(this)
    override val serviceConnectionListener = ServiceConnectionListener(this)
    override val nsdManager : NsdManager by lazy {
        (activity.getSystemService(NSD_SERVICE) as NsdManager)
    }
    override fun isEverythingStopped(): Boolean {
        return stoppedEverything.get()
    }
    fun startServerService() {
        activity.startService(Intent(activity, ServerService::class.java).apply{
            putExtra(ServerService.STOP_SERVICE_INTENT_EXTRA_KEY, false)
        })
    }
    private fun askForAllUpdates(messengerScope: MessengerScope, setIsLastMessage: Boolean = false) {
        sendMessage(messengerScope, MSG_ASK_ALL_UPDATE,
            setReplyTo = true, setIsLastMessage = setIsLastMessage)
    }
    fun askForClientProcessus(uuid: UUID, messengerScope: MessengerScope, setIsLastMessage: Boolean = false) {
        sendBundleMessage(messengerScope, MSG_ASK_CLIENT_PROCESSUS,
            bundleOf(CLIENT_UUID_BUNDLE_KEY to uuid), setReplyTo = true,
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
        onConnectedClientsCallback = callback
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
    override fun dlog(message: String) {
        Log.d(TAG, message)
        activity.messageToDebugTextViewAsync("$TAG>$message")
    }
    override fun toast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
    override fun sendRegisterClient(messengerScope: MessengerScope, setIsLastMessage: Boolean) {
        sendRegisterClientImpl(messengerScope, setIsLastMessage)
    }
    override fun onServerPortAssigned(port: Int) {
        localServerPort = port
        dlog("Local port is $port")
        registerService()
    }
    override fun messageToDebugTextViewAsync(message: String) {
        activity.messageToDebugTextViewAsync(message)
    }
    override fun makeIncomingHandler(looper: Looper): IncomingHandler {
        return IncomingHandler(this, looper)
    }
    override fun createAllUpdatesRequester() {
        allUpdatesRequester = timer(
            "CommManagerAllUpdatesRequester", initialDelay = 1000, period = 1000) {
            withServerMessengerScope { messengerScope ->
                askForAllUpdates(messengerScope, setIsLastMessage = true)
            }
        }
    }
}
