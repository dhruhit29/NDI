package org.mandir.tvreceiver

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

object Launcher {
    private const val TAG = "MandirTV"

    /**
     * Android 10+ only lets an app open a screen from the background if it has the
     * "display over other apps" permission. Fire TV has no menu for it, so it is
     * granted once from a PC (see windows/install-on-firetv.bat).
     */
    fun canStartFromBackground(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)

    fun openPlayer(context: Context) {
        if (Prefs(context).streamUrl.isBlank()) return
        if (!canStartFromBackground(context)) {
            Log.w(TAG, "Cannot open player from background: overlay permission not granted")
            return
        }
        val intent = Intent(context, PlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open player", e)
        }
    }
}
