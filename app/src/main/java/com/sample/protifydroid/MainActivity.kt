package com.sample.protifydroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.conversantmedia.util.concurrent.ConcurrentStack
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import java.util.*
import kotlin.concurrent.timer

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val CHANNEL_ID = "PROTIFY_NEW_NOTIF_CHANNEL_ID"
    }
    private val communicationManager = CommunicationManager(this)
    private var selectedClient: UUID = EMPTY_UUID
    private val notificationId: Int by lazy {
        Random().nextInt(100)
    }
    private val pendingMessages = ConcurrentStack<String>(0)
    private var debugViewFlushTimer: Timer? = null
    private fun dlog(message: String) {
        Log.d(TAG, message)
        messageToDebugTextViewAsync("$TAG>$message")
    }
    private var debugViewText = ""
    fun messageToDebugTextViewAsync(message: String, dontKeepPending: Boolean = false): Deferred<Boolean> {
        return CoroutineScope(Main).async {
            val view = findViewById<TextView?>(R.id.debugTextView)
            return@async if (view == null) {
                println("View is null, pendingMessages+1: ${pendingMessages.size()}")
                if (!dontKeepPending) {
                    pendingMessages.push(message)
                }
                false
            } else {
                debugViewText = "$debugViewText\n$message"
                view.text = debugViewText
                true
            }
        }
    }
//    fun onClientsUpdate() {
//        dlog("Asking for Connected Clients")
//        communicationManager.askForConnectedClients()
//        if (selectedClient.isNotEmpty()) {
//            dlog("Asking for client ${selectedClient}'s processus")
//            communicationManager.askForClientProcessus(selectedClient)
//        } else {
//            dlog("Selected client is empty")
//        }
//    }
    private fun onConnectedClientsReceived(connectedClients: List<Client>) {
//        dlog("selectedClient : $selectedClient, Received Connected Clients: $connectedClients")
        if (selectedClient.isEmpty() && connectedClients.isNotEmpty()) {
            selectedClient = connectedClients[0].uuid
            communicationManager.askForClientProcessus(selectedClient)
        }
        if (selectedClient.isNotEmpty() && !connectedClients.contains(selectedClient)) {
            if (connectedClients.isEmpty()) {
                selectedClient = EMPTY_UUID
                processusListAdapter.dataSet = listOf()
                processusListAdapter.notifyDataSetChanged()
            } else {
                selectedClient = connectedClients[0].uuid
                communicationManager.askForClientProcessus(selectedClient)
            }
        }
        runOnUiThread {
            clientsListAdapter.dataSet = connectedClients
            clientsListAdapter.notifyDataSetChanged()
        }
}
    private fun onClientProcessusReceived(clientProcessus: List<String>, clientUuid: UUID) {
        if (clientUuid == selectedClient) {
            runOnUiThread {
                processusListAdapter.dataSet = clientProcessus
                processusListAdapter.notifyDataSetChanged()
            }
        }
    }
    fun onClientClicked(position: Int) {
        selectedClient = clientsListAdapter.dataSet[position].uuid
        runOnUiThread {
            communicationManager.askForClientProcessus(selectedClient)
        }
    }
    fun onNewNotification(message: String) {
        // Create an explicit intent for an Activity in your app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_finished)
                .setContentTitle(message)
                .setContentText("")
                .setContentIntent(pendingIntent)
                .setChannelId(CHANNEL_ID)
                .setAutoCancel(true)
                .setColorized(true)
                .setColor(getColor(R.color.notification_color))
                .build()
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        with(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager) {
            // notificationId is a unique int for each notification that you must define
            dlog("\n\n\tNEW NOTIFICATION LAUNCHED!\n")
            notify(notificationId, notification)
        }
    }
    private val clientsListAdapter : ClientListViewAdapter by lazy {
        ClientListViewAdapter(this, listOf())
    }
    private val processusListAdapter : StringListViewAdapter by lazy {
        StringListViewAdapter(this, listOf())
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.new_notif_channel_name)
            val descriptionText = getString(R.string.new_notif_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        communicationManager.setOnConnectedClients(::onConnectedClientsReceived)
        communicationManager.setOnClientProcessus(::onClientProcessusReceived)
        dlog("Starting SERVER SERVICE....")
        communicationManager.startServerService()
        dlog("Server service STARTED, BINDING TO SERVER !")
        communicationManager.bindToServer()
    }
    override fun onStart() {
            super.onStart()
        debugViewFlushTimer = timer("flushToViewTimer",
            false,
            1000,
            3000) {
            val tmpStack = Stack<String>()
            pendingMessages.popEach {
                runBlocking {
                    CoroutineScope(Dispatchers.IO).launch {
                        if (!messageToDebugTextViewAsync(it, dontKeepPending = true).await()) {
                            tmpStack.push(it)
                        }
                    }
                }
            }
            tmpStack.forEach { pendingMessages.push(it) }
        }
        setContentView(R.layout.activity_main)
        findViewById<RecyclerView>(R.id.clientsListView).run {
            adapter = clientsListAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        findViewById<RecyclerView>(R.id.processusListView).run {
            adapter = processusListAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }
    override fun onPause() {
        super.onPause()
        communicationManager.pause()
    }
    override fun onResume() {
        super.onResume()
        communicationManager.resume()
    }
    override fun onDestroy() {
        println("[log] MainActivity: On Destroy")
        debugViewFlushTimer?.cancel()
        communicationManager.stopEverything()
        super.onDestroy()
    }
}
