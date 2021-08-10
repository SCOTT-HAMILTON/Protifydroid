package com.sample.protifydroid

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.*
import android.text.Html
import android.text.Spanned
import android.text.SpannedString
import android.widget.Toast
import androidx.core.os.bundleOf
import com.conversantmedia.util.concurrent.ConcurrentStack
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

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

class ServerService : Service() {
    /** Keeps track of all current registered clients.  */
//    var mClients = ArrayList<Pair<Messenger, BinderChecker>>()
    private val notificationId : Int by lazy {
        Random().nextInt(100)
    }
    private val pendingMessages = ConcurrentStack<String>(0)
    private var mAssignedport : Int? = null

    private var mServerHandler: Handler? = null
    private var mServerHandlerLooper: Looper? = null
    private var mServerHandlerThread: HandlerThread? = null
    private var serverRunnable: ServerRunnable? = null

//    private var mIncomingHandler: IncomingHandler? = null
//    private var mIncomingHandlerLooper: Looper? = null
//    private var mIncomingHandlerThread: HandlerThread? = null
//    private var mMessenger: Messenger? = null
    private val ServerUUID = UUID.randomUUID()
    /**
     * Handler of incoming messages from clients.
     */
    internal inner class IncomingHandler(private val service: ServerService,
                                             looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
//            toast("SERVER RECEIVED MESSAGE !!!")
            when (msg.what) {
                MSG_REGISTER_CLIENT -> {
                    toast("Received MSG_REGISTER_CLIENT")
//                    mClients.add(msg.replyTo, this@ServerService)
                    mAssignedport?.let {
                        service.sendPortMessage(msg.replyTo, it)
                        sendBundleMessage(msg.replyTo,
                            MSG_CONNECTED_CLIENTS_ANWSER, bundleOf(
                            CONNECTED_CLIENTS_BUNDLE_KEY to serverRunnable?.getConnectedClients()))
                    }
                    messageToDebugTextView(msg.replyTo,"Synchronizing with client, ${pendingMessages.size()} pending messages")
                    messageToDebugTextView(msg.replyTo, "ServerUUID = ${ServerUUID.toString()}")
                    messageToDebugTextView(msg.replyTo, "There are ${serverRunnable?.getConnectedClients()?.size} connected clients")
                    pendingMessages.popEach {
                        service.sendMessage(msg.replyTo, MSG_DEBUG_MESSAGE, it)
                    }
                    pendingMessages.clear()
                }
                MSG_UNREGISTER_CLIENT -> {
//                    val found = mClients.find { it.first == msg.replyTo }
//                    if (found != null) {
//                        mClients.remove(found)
//                    } else {
//                        toast("Can't delete client, messenger not found")
//                    }
                }
                MSG_ASK_CLIENT_PROCESSUS -> {
                    val uuid = msg.data[CLIENT_UUID_BUNDLE_KEY] as UUID
                    toast("ASKED: Client $uuid's Process Asked")
                    val client = serverRunnable?.getClient(uuid)
                    toast("Client $uuid's Process Found: ${client?.processus}")
                    client?.also {
                        sendBundleMessage(
                            msg.replyTo,
                            MSG_CLIENT_PROCESSUS_ANWSER, bundleOf(
                                PROCESSUS_BUNDLE_KEY to client.processus,
                                CLIENT_UUID_BUNDLE_KEY to client.uuid
                            )
                        )
                    }
                }
                MSG_ASK_CONNECTED_CLIENTS -> {
                    sendConnectedClients(msg.replyTo)
                }
                else -> super.handleMessage(msg)
            }
        }
    }
    fun toast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(
                this@ServerService, message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private fun createServerRunnable() {
        mServerHandlerThread = HandlerThread(
            UUID.randomUUID().toString(),
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply {
            start()
            mServerHandlerLooper = looper
            mServerHandler = Handler(looper)
            serverRunnable = ServerRunnable(this@ServerService)
            serverRunnable?.let {
                if (mServerHandler?.post(it) == false) {
                    toast("Failed to post Server Runnable")
                }
            }
        }
    }
    private fun destroyServerRunnable() {
        serverRunnable?.stopEverything()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mServerHandlerLooper?.quitSafely()
            mServerHandlerThread?.quitSafely()
        }
    }
    private fun sendConnectedClients(messenger: Messenger) {
        sendBundleMessage(
            messenger,
            MSG_CONNECTED_CLIENTS_ANWSER, bundleOf(
                CONNECTED_CLIENTS_BUNDLE_KEY to serverRunnable?.getConnectedClients()))
    }
    private fun createMessenger(): Messenger {
//        runBlocking {
//            ServiceIBinderLock.lock()
            HandlerThread(
                UUID.randomUUID().toString(),
                Process.THREAD_PRIORITY_BACKGROUND
            ).apply {
                start()
                return Messenger(IncomingHandler(this@ServerService, looper))
//                ServiceIBinder = messenger.binder
//                mMessenger = messenger
            }
//            ServiceIBinderLock.unlock()
//        }
    }
    fun createIBinder(): IBinder {
        return createMessenger().binder
    }
//    private fun destroyMessenger() {
//        runBlocking {
//            ServiceIBinderLock.lock()
//            ServiceIBinder = null
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
//                mIncomingHandlerThread?.quitSafely()
//                mIncomingHandlerLooper?.quitSafely()
//            }
//            mIncomingHandler = null
//            mIncomingHandlerLooper = null
//            mIncomingHandlerThread = null
//            ServiceIBinderLock.unlock()
//        }
//    }
    override fun onCreate() {
        SingletoneService = this
//        toast(getString(R.string.service_starting_text))
//        createMessenger()
        // Display a notification about us starting.
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.server_notif_channel_name)
            val descriptionText = getString(R.string.server_notif_channel_description)
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
        val htmlStopActionText: Spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(
                "<font color=\"" + getColor(R.color.notification_color) + "\">" +
                        getString(R.string.stop_server_notif_action_text) + "</font>",
                Html.FROM_HTML_MODE_LEGACY)
        } else {
            SpannedString(getString(R.string.stop_server_notif_action_text))
        }
        val trashIcon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Icon.createWithResource(this, R.drawable.ic_trash)
        } else {
            TODO("VERSION.SDK_INT < M")
        }
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.foreground_notification_title))
                .setContentText("")
                .setSmallIcon(R.drawable.ic_finished)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(stopServiceIntent)
                .addAction(Notification.Action.Builder(trashIcon,
                    htmlStopActionText, stopServiceIntent) .build())
                .setTicker(getString(R.string.foreground_notification_ticker_text))
                .setChannelId(CHANNEL_ID)
                .setColorized(true)
                .setColor(getColor(R.color.notification_color))
                .build()
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        createServerRunnable()
        startForeground(notificationId, notification)
        toast(getString(R.string.server_started_text))
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return if (intent.getBooleanExtra(STOP_SERVICE_INTENT_EXTRA_KEY, false)) {
            toast(getString(R.string.service_stopping_text))
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancelAll()
            stopSelf()
            START_NOT_STICKY
        } else {
            START_REDELIVER_INTENT
        }
    }
    override fun onDestroy() {
        println("ON DESTROY !!!!")
        // Tell the user we stopped.
//        destroyMessenger()
        destroyServerRunnable()
        toast(getString(R.string.service_stopped_text))
    }
    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    override fun onBind(intent: Intent): IBinder? {
        return createIBinder()
//        return runBlocking {
//            destroyMessenger()
//                toast("Sent valid Ibinder")
//            if (ServiceIBinder == null) {
//                toast("Error failed to create IBinder, it's null")
//            }
//            ServiceIBinder
//        }
    }
    /**
     * Alwaus returning true means that if the same activity rebinds,
     * onRebind will be called
     */
    override fun onUnbind(intent: Intent?): Boolean {
//        destroyMessenger()
        toast("Activity unbounded from service")
        super.onUnbind(intent)
        return true
    }
//    private fun sendMessage(code: Int) {
//        for (i in mClients.indices.reversed()) {
//            try {
//                val message = Message.obtain(
//                    null,
//                    code
//                )
//                if (!mClients[i].send(message)) {
//                    throw Exception()
//                }
//            } catch (e: Exception) {
//                // The client is dead.  Remove it from the list;
//                // we are going through the list from back to front
//                // so this is safe to do inside the loop.
//                mClients.removeAt(i)
//            }
//        }
//    }

    override fun onRebind(intent: Intent?) {
        toast("Activity rebounded to service")
        super.onRebind(intent)
    }
    fun sendBundleMessage(messenger: Messenger, code: Int, bundle: Bundle) {
        try {
            val message = Message.obtain(
                null,
                code
            ).apply {
                data = bundle
            }
            messenger.send(message)
        } catch (e: Exception) {
            toast("Failed to send Bundle Message: $e")
            // The client is dead.  Remove it from the list;
            // we are going through the list from back to front
            // so this is safe to do inside the loop.
        }
    }
    fun sendMessage(messenger: Messenger, code: Int, message: String) {
        sendBundleMessage(messenger, code, bundleOf(MESSAGE_BUNDLE_KEY to message))
    }
    fun sendPortMessage(messenger: Messenger, port: Int) {
        try {
            val message = Message.obtain(
                null,
                MSG_PORT_ASSIGNED, port, 0, null
            )
            messenger.send(message)
        } catch (e: Exception) {
            toast("Failed to send port message: $e")
        }
    }
    fun messageToDebugTextView(messenger: Messenger, message: String) {
//        if (mClients.size == 0) {
//            pendingMessages.push(message)
//        } else {
            sendMessage(messenger, MSG_DEBUG_MESSAGE, "ServerService> $message")
//        }
    }
//    fun onNewNotification(message: String) {
//        sendMessage(MSG_NEW_NOTIFICATION, message)
//    }
//    fun onClientsUpdate() {
//        sendMessage(MSG_CLIENTS_UPDATE)
//    }
    fun onServerPortAssigned(assignedPort: Int) {
        mAssignedport = assignedPort
//        sendPortMessage(assignedPort)
    }
    companion object {
//        val ServiceIBinderLock = Mutex(false)
//        var ServiceIBinder: IBinder? = null
        var SingletoneService: ServerService? = null
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
        const val MSG_ASK_CLIENT_PROCESSUS = 7
        const val MSG_CLIENT_PROCESSUS_ANWSER = 8
        const val MSG_ASK_CONNECTED_CLIENTS = 9
        const val MSG_CONNECTED_CLIENTS_ANWSER = 10

        const val CHANNEL_ID = "PROTIFY_SERVER_NOTIF_CHANNEL_ID"
        const val PROCESSUS_BUNDLE_KEY = "processus"
        const val CLIENT_UUID_BUNDLE_KEY = "clientUuid"
        const val CONNECTED_CLIENTS_BUNDLE_KEY = "connectedClients"
        const val MESSAGE_BUNDLE_KEY = "message"
        const val STOP_SERVICE_INTENT_EXTRA_KEY = "stopService"
    }
}
//END_INCLUDE(service)