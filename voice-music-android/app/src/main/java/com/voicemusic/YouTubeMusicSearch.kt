package com.voicemusic

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Finds the top YouTube Music song for a spoken query, using the same search endpoint the
 * music.youtube.com website uses. No account or API key needed.
 */
object YouTubeMusicSearch {
    private const val ENDPOINT = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val CLIENT_VERSION = "1.20250101.01.00"

    /** Search filter for "Songs" only, so an artist name plays their top song rather than a video. */
    private const val SONGS_FILTER = "EgWKAQIIAWoKEAoQCRADEAQQBRAV"

    private val VIDEO_ID = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")

    /** Blocking; call off the main thread. Returns the video id of the best match, or null. */
    fun findSong(query: String): String? = search(query, SONGS_FILTER) ?: search(query, null)

    private fun search(query: String, params: String?): String? {
        val locale = Locale.getDefault()
        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB_REMIX")
                        .put("clientVersion", CLIENT_VERSION)
                        .put("hl", locale.language.ifEmpty { "en" })
                        .put("gl", locale.country.ifEmpty { "US" }),
                ),
            )
            .put("query", query)
        if (params != null) body.put("params", params)

        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Origin", "https://music.youtube.com")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) VoiceMusic")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            // Results are listed best first; the first video id is the top result.
            return VIDEO_ID.find(response)?.groupValues?.get(1)
        } finally {
            connection.disconnect()
        }
    }
}
