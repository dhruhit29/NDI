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
