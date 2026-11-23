package org.mandir.tvreceiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * TVs with Fire TV built in rarely reboot: "off" on the remote is standby.
 * This small background service watches for the screen turning back on and
 * brings the player to the front, so nobody has to pick the app each morning.
 */
class KeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                handler.removeCallbacksAndMessages(null)
                // Give the Fire TV home screen a moment to appear first, then go on top of it.
                handler.postDelayed({ Launcher.openPlayer(this@KeepAliveService) }, WAKE_DELAY_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = notificationBuilder()
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun notificationBuilder(): Notification.Builder {
        if (Build.VERSION.SDK_INT < 26) return Notification.Builder(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_MIN)
        )
        return Notification.Builder(this, CHANNEL_ID)
    }

    companion object {
        private const val TAG = "MandirTV"
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_DELAY_MS = 3000L

        /** Start or stop the service to match the current settings. */
        fun sync(context: Context) {
            val prefs = Prefs(context)
            val intent = Intent(context, KeepAliveService::class.java)
            if (prefs.reopenOnWake && prefs.streamUrl.isNotBlank()) {
                try {
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start keep-alive service", e)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
