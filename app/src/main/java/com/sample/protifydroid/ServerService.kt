package com.sample.protifydroid

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.widget.Toast
import androidx.core.os.bundleOf
import java.util.*
import kotlin.concurrent.thread

/**
 * This is an example of implementing an application service that uses the
 * [Messenger] class for communicating with clients.  This allows for
 * remote interaction with a service, without needing to define an AIDL
 * interface.
 *
 *
 * Notice the use of the [NotificationManager] when interesting things
 * happen in the service.  This is generally how background services should
 * interact with the user, rather than doing something more disruptive such as
 * calling startActivity().
 */
class ServerService : Service() {
    /** Keeps track of all current registered clients.  */
    var mClients = ArrayList<Messenger>()
    private val notificationId : Int by lazy {
        Random().nextInt(100)
    }
    private var pendingMessagesText = mutableListOf<String>()
    private val serverRunnable: ServerRunnable by lazy {
        ServerRunnable(this)
    }
    private var m_assignedPort : Int? = null
    /**
     * Handler of incoming messages from clients.
     */
    internal inner class IncomingHandler(private val service: ServerService) : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_REGISTER_CLIENT -> {
                    mClients.add(msg.replyTo)
                    m_assignedPort?.let {
                        sendPortMessage(it)
                        sendBundleMessage(MSG_CONNECTED_CLIENTS_ANWSER, bundleOf(
                            CONNECTED_CLIENTS_BUNDLE_KEY to serverRunnable.getConnectedClient()))
                    }
                    pendingMessagesText.map{sendMessage(MSG_DEBUG_MESSAGE, it)}
                    pendingMessagesText.clear()
                }
                MSG_UNREGISTER_CLIENT -> mClients.remove(msg.replyTo)
                MSG_ASK_CLIENT_PROCESSUS -> {
                    val clientProcessus = serverRunnable.getClientProcessus(msg.arg1)
                    val connectedClients = serverRunnable.getConnectedClient()
                    if (msg.arg1 >= 0 && msg.arg1 < connectedClients.size) {
                        clientProcessus?.also {
                            sendBundleMessage(
                                MSG_CLIENT_PROCESSUS_ANWSER, bundleOf(
                                    PROCESSUS_BUNDLE_KEY to clientProcessus,
                                    CLIENT_NAME_BUNDLE_KEY to connectedClients[msg.arg1]
                                )
                            )
                        }
                    }
                }
                MSG_ASK_CONNECTED_CLIENTS -> {
                    sendBundleMessage(MSG_CONNECTED_CLIENTS_ANWSER, bundleOf(
                        CONNECTED_CLIENTS_BUNDLE_KEY to serverRunnable.getConnectedClient()))
                }
                else -> super.handleMessage(msg)
            }
        }
    }
    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    val mMessenger = Messenger(IncomingHandler(this))
    override fun onCreate() {
        // Display a notification about us starting.
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ChannelName2"
            val descriptionText = "ChannelDescription2"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        val stopServiceIntent: PendingIntent =
            Intent(this, ServerService::class.java).apply{
                putExtra(STOP_SERVICE_INTENT_EXTRA_KEY, true)
            }.let {
                PendingIntent.getService(this, 1, it, 0)
            }
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.foreground_notification_title))
                .setContentText("")
                .setSmallIcon(R.drawable.ic_finished)
                .setContentIntent(pendingIntent)
                .addAction(Notification.Action.Builder(R.drawable.ic_trash,
                    getString(R.string.stop_server_intent_action_text), stopServiceIntent).build())
                .setTicker(getString(R.string.foreground_notification_ticker_text))
                .setChannelId(CHANNEL_ID)
                .build()
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        thread {
            serverRunnable.run()
        }
        startForeground(notificationId, notification)
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return if (intent.getBooleanExtra(STOP_SERVICE_INTENT_EXTRA_KEY, false)) {
            Toast.makeText(this, getString(R.string.service_stopping_text),
                Toast.LENGTH_SHORT).show()
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancelAll()
            stopSelf()
            START_NOT_STICKY
        } else {
            START_STICKY
        }
    }
    override fun onDestroy() {
        // Tell the user we stopped.
        serverRunnable.stop()
        Toast.makeText(this, getString(R.string.service_stopped_text),
            Toast.LENGTH_LONG).show()
    }
    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    override fun onBind(intent: Intent): IBinder? {
        return mMessenger.binder
    }
    fun sendMessage(code: Int) {
        for (i in mClients.indices.reversed()) {
            try {
                mClients[i].send(
                    Message.obtain(
                        null,
                        code
                    )
                )
            } catch (e: RemoteException) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.removeAt(i)
            }
        }
    }
    fun sendBundleMessage(code: Int, bundle: Bundle) {
        for (i in mClients.indices.reversed()) {
            try {
                mClients[i].send(
                    Message.obtain(
                        null,
                        code
                    ).apply {
                        data = bundle
                    }
                ).apply {  }
            } catch (e: RemoteException) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.removeAt(i)
            }
        }
    }
    fun sendMessage(code: Int, message: String) {
        sendBundleMessage(code, bundleOf("message" to message))
    }
    fun sendPortMessage(port: Int) {
        for (i in mClients.indices.reversed()) {
            try {
                mClients[i].send(
                    Message.obtain(
                        null,
                        MSG_PORT_ASSIGNED, port, 0, null
                    )
                )
            } catch (e: RemoteException) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.removeAt(i)
            }
        }
    }
    fun messageToDebugTextView(message: String) {
        if (mClients.size == 0) {
            pendingMessagesText += message
        } else {
            sendMessage(MSG_DEBUG_MESSAGE, message)
        }
    }
    fun onNewNotification(message: String) {
        sendMessage(MSG_NEW_NOTIFICATION, message)
    }
    fun onClientsUpdate() {
        sendMessage(MSG_CLIENTS_UPDATE)
    }
    fun onServerPortAssigned(assignedPort: Int) {
        m_assignedPort = assignedPort
        sendPortMessage(assignedPort)
    }
    companion object {
        private const val TAG = "ServerRunnable";
        /**
         * Command to the service to register a client, receiving callbacks
         * from the service.  The Message's replyTo field must be a Messenger of
         * the client where callbacks should be sent.
         */
        const val MSG_REGISTER_CLIENT = 1
        /**
         * Command to the service to unregister a client, ot stop receiving callbacks
         * from the service.  The Message's replyTo field must be a Messenger of
         * the client as previously given with MSG_REGISTER_CLIENT.
         */
        const val MSG_UNREGISTER_CLIENT = 2
        const val MSG_PORT_ASSIGNED = 3
        const val MSG_DEBUG_MESSAGE = 4
        const val MSG_NEW_NOTIFICATION = 5
        const val MSG_CLIENTS_UPDATE = 6
        const val MSG_ASK_CLIENT_PROCESSUS = 7
        const val MSG_CLIENT_PROCESSUS_ANWSER = 8
        const val MSG_ASK_CONNECTED_CLIENTS = 9
        const val MSG_CONNECTED_CLIENTS_ANWSER = 10

        const val CHANNEL_ID = "com.sample.protifydroid.notif-serverservice-channelid"
        const val PROCESSUS_BUNDLE_KEY = "processus"
        const val CLIENT_NAME_BUNDLE_KEY = "clientName"
        const val CONNECTED_CLIENTS_BUNDLE_KEY = "connectedClients"
        const val STOP_SERVICE_INTENT_EXTRA_KEY = "stopService"
    }
}
//END_INCLUDE(service)