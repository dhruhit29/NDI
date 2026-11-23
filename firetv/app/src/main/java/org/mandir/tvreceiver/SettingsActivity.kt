package org.mandir.tvreceiver

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.net.Inet4Address

/** Simple remote-friendly settings screen: stream address and auto-open options. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var urlInput: EditText
    private lateinit var bootCheck: CheckBox
    private lateinit var wakeCheck: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_settings)

        urlInput = findViewById(R.id.url_input)
        bootCheck = findViewById(R.id.boot_check)
        wakeCheck = findViewById(R.id.wake_check)

        urlInput.setText(prefs.streamUrl)
        bootCheck.isChecked = prefs.startOnBoot
        wakeCheck.isChecked = prefs.reopenOnWake

        val saveButton = findViewById<Button>(R.id.save_button)
        saveButton.setOnClickListener { save() }
        findViewById<Button>(R.id.cancel_button).setOnClickListener { finish() }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveButton.requestFocus()
                true
            } else {
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val permissionButton = findViewById<Button>(R.id.permission_button)
        permissionButton.visibility =
            if (Launcher.canStartFromBackground(this)) View.GONE else View.VISIBLE
        permissionButton.setOnClickListener { requestOverlayPermission() }
        findViewById<TextView>(R.id.info_text).text = getString(
            R.string.info_text,
            localIpv4Addresses(),
            versionName(),
            permissionStatus(),
        )
    }

    private fun save() {
        val url = StreamUrl.normalize(urlInput.text.toString())
        if (url.isEmpty()) {
            urlInput.error = getString(R.string.error_empty_url)
            return
        }
        prefs.streamUrl = url
        prefs.startOnBoot = bootCheck.isChecked
        prefs.reopenOnWake = wakeCheck.isChecked
        KeepAliveService.sync(this)
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    /** Opens Android's "display over other apps" screen if this TV has one (many Fire TVs don't). */
    private fun requestOverlayPermission() {
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // try the next one
            } catch (e: SecurityException) {
                // try the next one
            }
        }
        Toast.makeText(this, R.string.no_permission_screen, Toast.LENGTH_LONG).show()
    }

    private fun permissionStatus(): String = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> getString(R.string.perm_not_needed)
        Launcher.canStartFromBackground(this) -> getString(R.string.perm_granted)
        else -> getString(R.string.perm_missing)
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    private fun localIpv4Addresses(): String {
        return try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val links = cm.getLinkProperties(cm.activeNetwork)
            links?.linkAddresses
                ?.map { it.address }
                ?.filterIsInstance<Inet4Address>()
                ?.mapNotNull { it.hostAddress }
                ?.joinToString(", ")
                ?.ifEmpty { null }
                ?: getString(R.string.unknown)
        } catch (e: Exception) {
            getString(R.string.unknown)
        }
    }
}
