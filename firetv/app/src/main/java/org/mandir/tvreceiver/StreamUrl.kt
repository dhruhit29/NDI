package org.mandir.tvreceiver

/**
 * Turns what a volunteer types on the remote into a full stream address.
 *
 *   "192.168.1.50"               -> rtsp://192.168.1.50:8554/program
 *   "192.168.1.50/hall2"         -> rtsp://192.168.1.50:8554/hall2
 *   "192.168.1.50:9000/program"  -> rtsp://192.168.1.50:9000/program
 *   anything with "://"          -> used exactly as typed
 */
object StreamUrl {
    const val DEFAULT_PORT = 8554
    const val DEFAULT_PATH = "program"
    const val HLS_PORT = 8888

    fun isRtsp(url: String): Boolean =
        url.startsWith("rtsp://", ignoreCase = true) || url.startsWith("rtsps://", ignoreCase = true)

    /**
     * MediaMTX serves the same stream as HLS on port 8888:
     *   rtsp://192.168.1.50:8554/program -> http://192.168.1.50:8888/program/index.m3u8
     * Returns null if [rtspUrl] is not an rtsp:// address.
     */
    fun hlsFallbackFor(rtspUrl: String): String? {
        if (!isRtsp(rtspUrl)) return null
        val afterScheme = rtspUrl.substringAfter("://")
        val hostPort = afterScheme.substringBefore('/')
        val host = if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
        if (host.isEmpty()) return null
        val path = afterScheme.substringAfter('/', DEFAULT_PATH).substringBefore('?').trim('/').ifEmpty { DEFAULT_PATH }
        return "http://$host:$HLS_PORT/$path/index.m3u8"
    }

    fun normalize(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return ""
        if (s.contains("://")) return s
        val hostPart = s.substringBefore('/')
        if (hostPart.isEmpty()) return ""
        val path = s.substringAfter('/', DEFAULT_PATH).ifEmpty { DEFAULT_PATH }
        val hostAndPort = if (hostPart.contains(':')) hostPart else "$hostPart:$DEFAULT_PORT"
        return "rtsp://$hostAndPort/$path"
    }
}
