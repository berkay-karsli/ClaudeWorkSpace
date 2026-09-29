package com.voicemusic

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Downloads and unpacks the offline Vosk speech model used for wake-phrase detection (~40 MB). */
object ModelManager {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    private const val COMPLETE_MARKER = ".complete"

    fun modelDir(context: Context) = File(context.filesDir, "vosk-model")

    fun isInstalled(context: Context) = File(modelDir(context), COMPLETE_MARKER).exists()

    /** Blocking. Call from a background thread. [onProgress] receives 0..100. */
    @Throws(IOException::class)
    fun download(context: Context, onProgress: (Int) -> Unit) {
        val zip = File(context.cacheDir, "model.zip")
        val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Download failed: HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            var done = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                zip.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            // First 90% is the download, the rest is unzipping.
                            val percent = (done * 90 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        val staging = File(context.filesDir, "vosk-model-staging")
        staging.deleteRecursively()
        unzipStrippingTopFolder(zip, staging)
        zip.delete()

        val target = modelDir(context)
        target.deleteRecursively()
        if (!staging.renameTo(target)) throw IOException("Could not install model")
        File(target, COMPLETE_MARKER).createNewFile()
        onProgress(100)
    }

    private fun unzipStrippingTopFolder(zip: File, into: File) {
        val root = into.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                val relative = entry.name.substringAfter('/', "")
                if (relative.isEmpty()) continue
                val out = File(into, relative)
                if (!out.canonicalPath.startsWith(root)) throw IOException("Bad zip entry: ${entry.name}")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { stream.copyTo(it) }
                }
            }
        }
    }
}
