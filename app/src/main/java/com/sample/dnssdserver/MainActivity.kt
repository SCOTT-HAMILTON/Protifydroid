package com.sample.protifydroid

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
        private const val CHANNEL_ID = "com.sample.protifydroid.notif-channelid"
    }
    private val communicationManager = CommunicationManager(this)
    private var listViewOnProcessus = false
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
        runOnUiThread {
            if (!listViewOnProcessus) {
                clientsListAdapter.dataSet = communicationManager.getConnectedClient()
                clientsListAdapter.notifyDataSetChanged()
            }
        }
    }
    fun onClientClicked(position: Int) {
        if (!listViewOnProcessus) {
            runOnUiThread {
                listViewOnProcessus = true
                clientsListAdapter.dataSet = communicationManager.getClientProcessus(position)
                clientsListAdapter.notifyDataSetChanged()
            }
        }
    }
    fun onNewNotification(message: String) {
        // Create an explicit intent for an Activity in your app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        var builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_finished)
            .setContentTitle(message)
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            notify(Random().nextInt(100), builder.build())
        }

    }
    override fun onBackPressed() {
        if (listViewOnProcessus) {
            runOnUiThread {
                listViewOnProcessus = false
                clientsListAdapter.dataSet = communicationManager.getConnectedClient()
                clientsListAdapter.notifyDataSetChanged()
            }
        } else {
            super.onBackPressed()
        }
    }
    private val clientsListAdapter : StringListViewAdapter by lazy {
        StringListViewAdapter(this, communicationManager.getConnectedClient())
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ChannelName"
            val descriptionText = "ChannelDescription"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        setContentView(R.layout.activity_main)
        communicationManager.startServer()
        findViewById<RecyclerView>(R.id.clientsListView).run {
            adapter = clientsListAdapter
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
