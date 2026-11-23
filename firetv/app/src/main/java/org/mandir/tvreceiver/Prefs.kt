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

    private companion object {
        const val KEY_URL = "stream_url"
        const val KEY_BOOT = "start_on_boot"
        const val KEY_WAKE = "reopen_on_wake"
    }
}
