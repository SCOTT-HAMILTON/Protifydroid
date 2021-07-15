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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val CHANNEL_ID = "PROTIFY_NEW_NOTIF_CHANNEL_ID"
    }
    private val communicationManager = CommunicationManager(this)
    private var selectedClient = ""
    private val notificationId: Int by lazy {
        Random().nextInt(100)
    }
    private fun dlog(message: String) {
        Log.d(TAG, message)
        messageToDebugTextView("$TAG>$message")
    }
    fun messageToDebugTextView(message: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.debugTextView).run {
                text = "${text.toString()}\n$message"
            }
        }
    }
    fun onClientsUpdate() {
        dlog("Asking for Connected Clients")
        communicationManager.askForConnectedClients()
        if (selectedClient != "") {
            val index = clientsListAdapter.dataSet.indexOf(selectedClient)
            if (index != -1) {
                communicationManager.askForClientProcessus(index)
            }
        }
    }
    fun onConnectedClientsReceived(connectedClients: List<String>) {
        dlog("selectedClient : $selectedClient, Received Connected Clients: $connectedClients")
        if (selectedClient == "" && connectedClients.isNotEmpty()) {
            selectedClient = connectedClients[0]
            dlog("selectedClient : $selectedClient, Asking for Client processus")
            communicationManager.askForClientProcessus(0)
        }
        if (selectedClient != "" && !connectedClients.contains(selectedClient)) {
            if (connectedClients.isEmpty()) {
                selectedClient = ""
                processusListAdapter.dataSet = listOf()
                processusListAdapter.notifyDataSetChanged()
            } else {
                selectedClient = connectedClients[0]
                communicationManager.askForClientProcessus(0)
            }
        }
        runOnUiThread {
            clientsListAdapter.dataSet = connectedClients
            clientsListAdapter.notifyDataSetChanged()
        }
}
    fun onClientProcessusReceived(processus: List<String>, clientName: String) {
        if (clientName == selectedClient) {
            runOnUiThread {
                processusListAdapter.dataSet = processus
                processusListAdapter.notifyDataSetChanged()
            }
        }
    }
    fun onClientClicked(position: Int) {
        selectedClient = clientsListAdapter.dataSet[position]
        runOnUiThread {
            communicationManager.askForClientProcessus(position)
        }
    }
    fun onNewNotification(message: String) {
        // Create an explicit intent for an Activity in your app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        var notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    private val clientsListAdapter : StringListViewAdapter by lazy {
        StringListViewAdapter(this, listOf())
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
        communicationManager.startServerService()
        communicationManager.bindToServer()
    }
    override fun onStart() {
            super.onStart()
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
        communicationManager.stopEverything()
        super.onDestroy()
    }
}
