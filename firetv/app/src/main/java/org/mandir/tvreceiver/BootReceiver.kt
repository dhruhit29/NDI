package org.mandir.tvreceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Opens the player when the TV boots, and keeps the wake-watcher running after updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        if (prefs.streamUrl.isBlank()) return
        KeepAliveService.sync(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> if (prefs.startOnBoot) Launcher.openPlayer(context)
        }
    }
}
