package com.sample.protifydroid

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception
import java.util.concurrent.atomic.AtomicBoolean

/* class to know if a binder is still alive */
class BinderChecker(private val context: Context) : IBinder.DeathRecipient {
    private val died: AtomicBoolean = AtomicBoolean(false)
    fun isBinderDead(): Boolean {
        return died.get()
    }
    override fun binderDied() {
        Toast.makeText(context, "Binder DIED!!!", Toast.LENGTH_SHORT).show()
        died.set(true)
    }
}

interface ServiceConnectionReceiver: LogReceiver {
    var isBound: Boolean
    fun sendRegisterClient(messengerScope: MessengerScope, setIsLastMessage: Boolean = false)
    fun createAllUpdatesRequester()
    val serviceConnectionListener: ServiceConnectionListener
}

class ServiceConnectionListener(private val receiver: ServiceConnectionReceiver) : ServiceConnection {
    override fun onServiceConnected(
        className: ComponentName,
        service: IBinder
    ) {
        withServerMessengerScope { messengerScope ->
            receiver.sendRegisterClient(messengerScope)
        }
        receiver.isBound = true
        receiver.createAllUpdatesRequester()
        receiver.dlog("Connected to server service")
    }
    override fun onServiceDisconnected(className: ComponentName) {
        receiver.toast("Server Service disconnected")
    }
    override fun onBindingDied(name: ComponentName?) {
        receiver.dlog("Binding died on component: $name")
        super.onBindingDied(name)
    }
    override fun onNullBinding(name: ComponentName?) {
        receiver.dlog("Null binding on component: $name")
        super.onNullBinding(name)
    }
}

abstract class ServerBinder(private val activity: Activity):
    LogReceiver,
    ServiceConnectionReceiver,
    IncomingHandlerReceiver {
    fun bindToServer() {
        dlog("Binding to service...")
        try {
            activity.bindService(
                Intent(
                    activity,
                    ServerService::class.java,
                ),
                serviceConnectionListener, Context.BIND_ABOVE_CLIENT or Context.BIND_AUTO_CREATE or Context.BIND_DEBUG_UNBIND,
            )
        } catch (e: SecurityException) {
            dlog("Security Exception when binding to service")
        }
    }
    fun unbindFromServer() {
        if (isBound) {
            CoroutineScope(Dispatchers.IO).launch {
                withServerMessengerScope { messengerScope ->
                    try {
                        val msg: Message = Message.obtain(
                            null,
                            MSG_UNREGISTER_CLIENT
                        ).apply {
                            replyTo = createMessenger { makeIncomingHandler(it) }
                            setLastMessage(messengerScope.uuid)
                        }
                        messengerScope.messenger.send(msg)
                    } catch (e: Exception) {
                        dlog("Failed to send unbind message: $e")
                    }
                }
            }
            // Detach our existing connection.
            activity.unbindService(serviceConnectionListener)
            isBound = false
        }
    }
}