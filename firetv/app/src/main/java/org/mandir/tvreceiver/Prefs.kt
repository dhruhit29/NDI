package org.mandir.tvreceiver

import android.content.Context

/** Settings stored on the TV. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("mandirtv", Context.MODE_PRIVATE)

    var streamUrl: String
        get() = sp.getString(KEY_URL, "") ?: ""
        set(value) = sp.edit().putString(KEY_URL, value).apply()

    var startOnBoot: Boolean
        get() = sp.getBoolean(KEY_BOOT, true)
        set(value) = sp.edit().putBoolean(KEY_BOOT, value).apply()

    var reopenOnWake: Boolean
        get() = sp.getBoolean(KEY_WAKE, true)
        set(value) = sp.edit().putBoolean(KEY_WAKE, value).apply()

    /** Set automatically when HLS worked and RTSP did not on this TV. */
    var preferHls: Boolean
        get() = sp.getBoolean(KEY_PREFER_HLS, false)
        set(value) {
            if (value != preferHls) sp.edit().putBoolean(KEY_PREFER_HLS, value).apply()
        }

    /** "RTSP" or "HLS": what the feed last played over. Shown in settings. */
    var lastMode: String
        get() = sp.getString(KEY_LAST_MODE, "") ?: ""
        set(value) {
            if (value != lastMode) sp.edit().putString(KEY_LAST_MODE, value).apply()
        }

    /** Last reason RTSP failed, shown in settings to help diagnose. */
    var lastRtspError: String
        get() = sp.getString(KEY_LAST_RTSP_ERROR, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_RTSP_ERROR, value).apply()

    private companion object {
        const val KEY_URL = "stream_url"
        const val KEY_BOOT = "start_on_boot"
        const val KEY_WAKE = "reopen_on_wake"
        const val KEY_PREFER_HLS = "prefer_hls"
        const val KEY_LAST_MODE = "last_mode"
        const val KEY_LAST_RTSP_ERROR = "last_rtsp_error"
    }
}
