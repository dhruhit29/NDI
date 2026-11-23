package org.mandir.tvreceiver

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A tiny pass-through RTSP relay on 127.0.0.1 that fixes one thing the player can't parse.
 *
 * MediaMTX (via gortsplib) sends an empty session name as `s= ` (a single space, as RFC 4566
 * recommends). Media3's SDP parser rejects that line ("Malformed SDP line: s= "), so RTSP
 * playback fails. This relay rewrites it to `s=-` (same length) in the DESCRIBE response and
 * passes everything else through untouched. Once interleaved RTP data starts (a '$' byte at a
 * message boundary) it becomes a plain byte pipe.
 */
class RtspSdpFixProxy(private val targetHost: String, private val targetPort: Int) : Closeable {

    private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
    private val openSockets = CopyOnWriteArrayList<Socket>()
    @Volatile private var closed = false

    val localPort: Int get() = server.localPort

    fun start(): RtspSdpFixProxy {
        thread(name = "rtsp-proxy-accept", isDaemon = true) {
            while (!closed) {
                val client = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                handle(client)
            }
        }
        return this
    }

    private fun handle(client: Socket) {
        thread(name = "rtsp-proxy-conn", isDaemon = true) {
            val upstream = Socket()
            openSockets += client
            openSockets += upstream
            try {
                client.tcpNoDelay = true
                upstream.tcpNoDelay = true
                upstream.connect(InetSocketAddress(targetHost, targetPort), CONNECT_TIMEOUT_MS)
            } catch (e: IOException) {
                Log.w(TAG, "Relay could not reach $targetHost:$targetPort", e)
                closeQuietly(client, upstream)
                return@thread
            }
            thread(name = "rtsp-proxy-up", isDaemon = true) {
                try {
                    copy(client.getInputStream(), upstream.getOutputStream())
                } catch (_: IOException) {
                } finally {
                    closeQuietly(client, upstream)
                }
            }
            try {
                relayServerToClient(upstream.getInputStream(), client.getOutputStream())
            } catch (_: IOException) {
            } finally {
                closeQuietly(client, upstream)
            }
        }
    }

    /** Parses whole RTSP responses until RTP data starts, fixing the SDP body; then plain copy. */
    private fun relayServerToClient(rawInput: InputStream, output: OutputStream) {
        val input = BufferedInputStream(rawInput, BUFFER_SIZE)
        while (true) {
            input.mark(1)
            val first = input.read()
            if (first < 0) return
            input.reset()
            if (first == '$'.code) break // interleaved RTP/RTCP from here on

            val header = readHeader(input) ?: return
            val headerText = String(header, Charsets.ISO_8859_1)
            val contentLength = CONTENT_LENGTH.find(headerText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val body = readExactly(input, contentLength) ?: return
            output.write(header)
            output.write(if (contentLength > 0) fixSdp(body) else body)
            output.flush()
        }
        copy(input, output)
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buf = ByteArray(BUFFER_SIZE)
        while (true) {
            val n = input.read(buf)
            if (n < 0) return
            output.write(buf, 0, n)
            output.flush()
        }
    }

    override fun close() {
        closed = true
        try {
            server.close()
        } catch (_: IOException) {
        }
        openSockets.forEach { closeQuietly(it) }
        openSockets.clear()
    }

    private fun closeQuietly(vararg sockets: Socket) {
        for (s in sockets) {
            try {
                s.close()
            } catch (_: IOException) {
            }
        }
    }

    companion object {
        private const val TAG = "MandirTV"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_HEADER_BYTES = 64 * 1024
        private const val MAX_BODY_BYTES = 1024 * 1024
        private val CONTENT_LENGTH = Regex("(?im)^content-length:\\s*(\\d+)")

        /** Reads up to and including the blank line that ends an RTSP header block. */
        private fun readHeader(input: InputStream): ByteArray? {
            val out = ByteArrayOutputStream(512)
            var last4 = 0
            while (out.size() < MAX_HEADER_BYTES) {
                val b = input.read()
                if (b < 0) return null
                out.write(b)
                last4 = (last4 shl 8) or b
                if (last4 == 0x0D0A0D0A) return out.toByteArray()
            }
            return null
        }

        private fun readExactly(input: InputStream, length: Int): ByteArray? {
            if (length <= 0) return ByteArray(0)
            if (length > MAX_BODY_BYTES) return null
            val data = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(data, read, length - read)
                if (n < 0) return null
                read += n
            }
            return data
        }

        /** `s= ` (blank session name) -> `s=-`. Same length, so Content-Length stays valid. */
        internal fun fixSdp(body: ByteArray): ByteArray {
            val out = body.copyOf()
            var i = 0
            while (i + 3 < out.size) {
                val lineStart = i == 0 || out[i - 1] == '\n'.code.toByte()
                if (lineStart &&
                    out[i] == 's'.code.toByte() &&
                    out[i + 1] == '='.code.toByte() &&
                    out[i + 2] == ' '.code.toByte() &&
                    (out[i + 3] == '\r'.code.toByte() || out[i + 3] == '\n'.code.toByte())
                ) {
                    out[i + 2] = '-'.code.toByte()
                }
                i++
            }
            return out
        }
    }
}
