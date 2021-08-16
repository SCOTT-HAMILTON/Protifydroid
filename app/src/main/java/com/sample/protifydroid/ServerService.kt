package com.sample.protifydroid

import android.app.*
import android.app.Notification.PRIORITY_HIGH
import android.app.Notification.PRIORITY_MAX
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
import java.util.*

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
    private val notificationId : Int by lazy {
        Random().nextInt(100)
    }
    private val pendingMessages = ConcurrentStack<String>(0)
    private var mAssignedport : Int? = null

    private var mServerRunnableHandlerScope: HandlerScope? = null
    private var serverRunnable: ServerRunnable? = null
    private val messengerScopePool = MessengerScopePool()

    private val debugServerUUID = UUID.randomUUID()
    /**
     * Handler of incoming messages from clients.
     */
    internal inner class IncomingHandler(private val service: ServerService,
                                             looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
//            toast("SERVER RECEIVED MESSAGE !!!")
            when (msg.what) {
                MSG_REGISTER_CLIENT -> {
//                    toast("Received MSG_REGISTER_CLIENT")
                    mAssignedport?.let { assignedPort ->
                        service.sendPortMessage(msg.replyTo, assignedPort)
                        sendBundleMessage(msg.replyTo,
                            MSG_CONNECTED_CLIENTS_ANWSER, bundleOf(
                            CONNECTED_CLIENTS_BUNDLE_KEY to serverRunnable?.getConnectedClients()))
                    }
                    messageToDebugTextView(msg.replyTo,"Synchronizing with client, ${pendingMessages.size()} pending messages")
                    messageToDebugTextView(msg.replyTo, "ServerUUID = $debugServerUUID")
                    messageToDebugTextView(msg.replyTo, "There are ${serverRunnable?.getConnectedClients()?.size} connected clients")
                    pendingMessages.popEach {
                        service.sendMessage(msg.replyTo, MSG_DEBUG_MESSAGE, it)
                    }
                    pendingMessages.clear()
                }
                MSG_UNREGISTER_CLIENT -> {
                }
                MSG_ASK_ALL_UPDATE -> {
                    sendConnectedClients(msg.replyTo)
                }
                MSG_ASK_CLIENT_PROCESSUS -> {
                    val uuid = msg.data[CLIENT_UUID_BUNDLE_KEY] as UUID
//                    toast("ASKED: Client $uuid's Process Asked")
                    val client = serverRunnable?.getClient(uuid)
//                    toast("Client $uuid's Process Found: ${client?.processus}")
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
                else -> {
                    toast("Received Unknown Message Code: ${msg.what}")
                    super.handleMessage(msg)
                }
            }
            if (msg.arg2 == MSG_SCOPE_UNUSED) {
                val uuid = msg.data[SCOPE_UUID_BUNDLE_KEY] as UUID
//                toast("Destroying scope: $uuid")
                messengerScopePool.destroyScope(uuid)
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
    private fun destroyServerRunnable() {
        serverRunnable?.stopEverything()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mServerRunnableHandlerScope?.looper?.quitSafely()
            mServerRunnableHandlerScope?.thread?.quitSafely()
        }
    }
    fun onNewNotifications() {
        val notifications = serverRunnable?.getNotifications()
        notifications?.forEach {
            spawnNotification(it)
        }
    }
    private fun sendConnectedClients(messenger: Messenger) {
        sendBundleMessage(
            messenger,
            MSG_CONNECTED_CLIENTS_ANWSER, bundleOf(
                CONNECTED_CLIENTS_BUNDLE_KEY to serverRunnable?.getConnectedClients()))
    }
    private fun createHandlerScope(handlerMaker: (service: ServerService, looper: Looper)->Handler):
            HandlerScope {
        val handlerThread = HandlerThread(
            UUID.randomUUID().toString(),
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply {
            start()
        }
        val handler = handlerMaker(this, handlerThread.looper)
        return HandlerScope(handler, handlerThread.looper, handlerThread)
    }
    fun createMessengerScope(): MessengerScope {
        val handlerScope = createHandlerScope { service, looper ->
            IncomingHandler(service, looper)}
        val messenger = Messenger(handlerScope.handler)
        val scope = MessengerScope(messenger, handlerScope, UUID.randomUUID())
        messengerScopePool.registerScope(scope)
        return scope
    }
    private fun createServerRunnable() {
        mServerRunnableHandlerScope = createHandlerScope { _, looper ->  Handler(looper) }
        serverRunnable = ServerRunnable(this)
        serverRunnable?.let { serverRunnable ->
            mServerRunnableHandlerScope?.handler?.let { handler ->
                if (!handler.post(serverRunnable)) {
                    toast("Failed to post Server Runnable")
                }
            }
        }
    }
    override fun onCreate() {
        toast("Service.onCreate")
        SingletoneService = this
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            run {
                val name = getString(R.string.server_notif_channel_name)
                val descriptionText = getString(R.string.server_notif_channel_description)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel =
                    NotificationChannel(FOREGROUND_NOTIF_CHANNEL_ID, name, importance).apply {
                        description = descriptionText
                    }
                // Register the channel with the system
                val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
            run {
                val name = getString(R.string.new_notif_channel_name)
                val descriptionText = getString(R.string.new_notif_channel_description)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(PROCESS_DIED_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableLights(true)
                    enableVibration(true)
                }
                // Register the channel with the system
                val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
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
            Notification.Builder(this, FOREGROUND_NOTIF_CHANNEL_ID)
                .setContentTitle(getString(R.string.foreground_notification_title))
                .setContentText("")
                .setSmallIcon(R.drawable.ic_finished)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(stopServiceIntent)
                .addAction(Notification.Action.Builder(trashIcon,
                    htmlStopActionText, stopServiceIntent) .build())
                .setTicker(getString(R.string.foreground_notification_ticker_text))
                .setChannelId(FOREGROUND_NOTIF_CHANNEL_ID)
                .setAutoCancel(false)
                .setColorized(true).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        setPriority(PRIORITY_MAX)
                    }
                }
                .setColor(getColor(R.color.notification_color))
                .build()
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        createServerRunnable()
        startForeground(notificationId, notification)
        toast(getString(R.string.server_started_text))
    }
    private fun spawnNotification(process: String) {
        // Create an explicit intent for an Activity in your app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, PROCESS_DIED_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_finished)
                .setContentTitle(getString(R.string.notif_process_died_template).format(process))
                .setContentText("")
                .setContentIntent(pendingIntent)
                .setChannelId(PROCESS_DIED_CHANNEL_ID)
                .setAutoCancel(true)
                .addAction(Notification.Action.Builder(trashIcon,
                    htmlStopActionText, stopServiceIntent) .build())
                .setColorized(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        setPriority(PRIORITY_HIGH)
                    }
                }
                .setColor(getColor(R.color.notification_color))
                .build()
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        with(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager) {
            // notificationId is a unique int for each notification that you must define
            notify(notificationId, notification)
        }
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        toast("Service.onStartCommand")
        return if (intent.getBooleanExtra(STOP_SERVICE_INTENT_EXTRA_KEY, false)) {
//            toast(getString(R.string.service_stopping_text))
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
        toast("Service.onDestroy")
        destroyServerRunnable()
//        toast(getString(R.string.service_stopped_text))
    }
    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    override fun onBind(intent: Intent): IBinder {
        toast("Service.onBind")
        return createMessengerScope().messenger.binder
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
        sendMessage(messenger, MSG_DEBUG_MESSAGE, "ServerService> $message")
    }
    fun onServerPortAssigned(assignedPort: Int) {
        mAssignedport = assignedPort
    }
    companion object {
        var SingletoneService: ServerService? = null

        const val FOREGROUND_NOTIF_CHANNEL_ID = "PROTIFY_SERVER_NOTIF_CHANNEL_ID"
        const val PROCESS_DIED_CHANNEL_ID = "PROTIFY_SERVER_PROCESS_DIED_CHANNEL_ID"

        const val STOP_SERVICE_INTENT_EXTRA_KEY = "stopService"
    }
}
//END_INCLUDE(service)