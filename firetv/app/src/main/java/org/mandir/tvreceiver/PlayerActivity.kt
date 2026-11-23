package org.mandir.tvreceiver

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView

/**
 * Full-screen, no-menu player for the program feed.
 *
 * - Plays the configured stream (normally rtsp://<program-pc>:8554/program).
 * - Reconnects forever if the stream drops or the program PC restarts.
 * - Restarts the stream if it freezes or falls more than a few seconds behind live.
 * - OK / Menu on the remote opens settings. Back must be pressed twice to exit.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var playerView: PlayerView
    private lateinit var statusPanel: View
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null
    private var retryDelayMs = RETRY_MIN_MS
    private var lastBackPressAt = 0L
    private var openedSettingsForMissingUrl = false

    // RTSP -> HLS fallback. Some TVs / streams fail over RTSP; MediaMTX serves the
    // same feed as HLS too (a little more delay, but very robust).
    private var useHlsFallback = false
    private var failuresInCurrentMode = 0
    private var reachedReady = false
    private var currentUrl = ""

    // Freeze / delay detection.
    private var lastPositionMs = -1L
    private var lastProgressAt = 0L
    private var playStartWallMs = -1L
    private var playStartPositionMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.player_view)
        statusPanel = findViewById(R.id.status_panel)
        statusText = findViewById(R.id.status_text)
        applyUrlFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyUrlFromIntent(intent) && player != null) {
            resetMode()
            startPlayback()
        }
    }

    override fun onStart() {
        super.onStart()
        hideSystemUi()
        KeepAliveService.sync(this)
        if (prefs.streamUrl.isBlank()) {
            showStatus(getString(R.string.status_no_url))
            if (!openedSettingsForMissingUrl) {
                openedSettingsForMissingUrl = true
                openSettings()
            }
            return
        }
        resetMode()
        startPlayback()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    /** `adb shell am start -n org.mandir.tvreceiver/.PlayerActivity -e url rtsp://...` sets the address. */
    private fun applyUrlFromIntent(intent: Intent?): Boolean {
        val raw = intent?.getStringExtra(EXTRA_URL) ?: return false
        intent.removeExtra(EXTRA_URL)
        val url = StreamUrl.normalize(raw)
        if (url.isEmpty()) return false
        prefs.streamUrl = url
        Log.i(TAG, "Stream address set from intent: $url")
        return true
    }

    private fun startPlayback() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()

        val configured = prefs.streamUrl
        val url = if (useHlsFallback) StreamUrl.hlsFallbackFor(configured) ?: configured else configured
        currentUrl = url
        reachedReady = false
        if (url.isBlank()) {
            showStatus(getString(R.string.status_no_url))
            return
        }
        showStatus(getString(R.string.status_connecting, url))

        // Small buffers: this is a live feed on a local network, so we want to
        // stay close to live rather than protect against internet-style jitter.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, START_BUFFER_MS, REBUFFER_MS)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val newPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
        newPlayer.addListener(listener)
        try {
            newPlayer.setMediaSource(buildMediaSource(url))
        } catch (e: Exception) {
            Log.e(TAG, "Bad stream address: $url", e)
            newPlayer.release()
            scheduleReconnect(getString(R.string.reason_bad_url))
            return
        }
        newPlayer.playWhenReady = true
        newPlayer.prepare()
        playerView.player = newPlayer
        player = newPlayer

        lastPositionMs = -1L
        lastProgressAt = SystemClock.elapsedRealtime()
        playStartWallMs = -1L
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
    }

    private fun buildMediaSource(url: String): MediaSource {
        val item = MediaItem.fromUri(url)
        return if (StreamUrl.isRtsp(url)) {
            RtspMediaSource.Factory()
                // RTP over the RTSP TCP connection: no lost packets / smearing,
                // and only one port (8554) has to be open on the program PC.
                .setForceUseRtpTcp(true)
                .setTimeoutMs(RTSP_TIMEOUT_MS)
                .createMediaSource(item)
        } else {
            // HLS (http://.../index.m3u8), plain MPEG-TS over UDP (udp://...), etc.
            DefaultMediaSourceFactory(this).createMediaSource(item)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    hideStatus()
                    retryDelayMs = RETRY_MIN_MS
                    reachedReady = true
                    failuresInCurrentMode = 0
                    Log.i(TAG, "Playing $currentUrl")
                }
                Player.STATE_ENDED -> scheduleReconnect(getString(R.string.reason_ended))
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && playStartWallMs < 0) {
                playStartWallMs = SystemClock.elapsedRealtime()
                playStartPositionMs = player?.currentPosition ?: 0L
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback error on $currentUrl", error)
            scheduleReconnect(describe(error))
        }
    }

    /**
     * Runs every few seconds while a stream is open:
     * - if the picture has not moved for [STALL_TIMEOUT_MS], reconnect;
     * - if playback has fallen more than [MAX_DRIFT_MS] behind real time
     *   (after network hiccups), reconnect to jump back to live.
     */
    private val watchdog = object : Runnable {
        override fun run() {
            val p = player ?: return
            val now = SystemClock.elapsedRealtime()
            val position = p.currentPosition
            if (position != lastPositionMs) {
                lastPositionMs = position
                lastProgressAt = now
            }
            if (now - lastProgressAt > STALL_TIMEOUT_MS) {
                scheduleReconnect(getString(R.string.reason_stalled))
                return
            }
            if (playStartWallMs >= 0) {
                val drift = (now - playStartWallMs) - (position - playStartPositionMs)
                if (drift > MAX_DRIFT_MS) {
                    Log.i(TAG, "Fell ${drift}ms behind live, reconnecting")
                    retryDelayMs = RETRY_MIN_MS
                    startPlayback()
                    return
                }
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun scheduleReconnect(reason: String) {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        if (!reachedReady) {
            failuresInCurrentMode++
            // Switch between RTSP and HLS after repeated failures to start.
            if (failuresInCurrentMode >= FAILURES_BEFORE_SWITCH &&
                StreamUrl.hlsFallbackFor(prefs.streamUrl) != null
            ) {
                useHlsFallback = !useHlsFallback
                failuresInCurrentMode = 0
                retryDelayMs = RETRY_MIN_MS
                Log.i(TAG, "Switching to ${if (useHlsFallback) "HLS" else "RTSP"}")
            }
        }
        showStatus(getString(R.string.status_reconnecting, currentUrl, reason, retryDelayMs / 1000))
        handler.postDelayed({ startPlayback() }, retryDelayMs)
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_MS)
    }

    private fun resetMode() {
        retryDelayMs = RETRY_MIN_MS
        useHlsFallback = false
        failuresInCurrentMode = 0
    }

    /** Error code plus the underlying reasons, so a volunteer can read it out. */
    private fun describe(error: PlaybackException): String {
        val parts = mutableListOf(error.errorCodeName)
        var cause: Throwable? = error.cause
        var depth = 0
        while (cause != null && depth < 4) {
            val msg = cause.message?.takeIf { it.isNotBlank() }
            val text = if (msg != null) "${cause.javaClass.simpleName}: $msg" else cause.javaClass.simpleName
            if (parts.none { it.contains(text) }) parts.add(text)
            cause = cause.cause
            depth++
        }
        return parts.joinToString("\n").take(MAX_ERROR_CHARS)
    }

    private fun releasePlayer() {
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
        playerView.player = null
    }

    private fun showStatus(message: String) {
        statusText.text = message
        statusPanel.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        statusPanel.visibility = View.GONE
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                openSettings()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackPressAt < BACK_EXIT_WINDOW_MS) {
                    finish()
                } else {
                    lastBackPressAt = now
                    Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    companion object {
        private const val TAG = "MandirTV"
        const val EXTRA_URL = "url"

        private const val MIN_BUFFER_MS = 1000
        private const val MAX_BUFFER_MS = 3000
        private const val START_BUFFER_MS = 250
        private const val REBUFFER_MS = 500
        private const val FAILURES_BEFORE_SWITCH = 2
        private const val MAX_ERROR_CHARS = 400
        private const val RTSP_TIMEOUT_MS = 8000L

        private const val RETRY_MIN_MS = 2000L
        private const val RETRY_MAX_MS = 10000L
        private const val WATCHDOG_INTERVAL_MS = 2000L
        private const val STALL_TIMEOUT_MS = 15000L
        private const val MAX_DRIFT_MS = 3000L
        private const val BACK_EXIT_WINDOW_MS = 2500L
    }
}
