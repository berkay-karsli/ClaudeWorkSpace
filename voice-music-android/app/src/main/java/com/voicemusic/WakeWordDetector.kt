package com.voicemusic

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Listens to the microphone continuously and fully offline, and calls [onDetected] when the wake
 * phrase is heard. Recognition is restricted to the wake phrase plus "[unk]" (anything else), which
 * keeps it cheap on the battery and makes the phrase easy to spot.
 */
class WakeWordDetector(
    model: Model,
    wakePhrase: String,
    private val onDetected: () -> Unit,
) : RecognitionListener {

    private val phrase = Prefs.normalizePhrase(wakePhrase)
    private val recognizer = Recognizer(model, SAMPLE_RATE, JSONArray().put(phrase).put("[unk]").toString())
    private var speechService: SpeechService? = null
    private var triggered = false

    fun start() {
        val service = speechService ?: SpeechService(recognizer, SAMPLE_RATE).also { speechService = it }
        triggered = false
        recognizer.reset()
        service.startListening(this)
    }

    /** Releases the microphone so another recognizer can use it. */
    fun pause() {
        speechService?.stop()
    }

    fun shutdown() {
        speechService?.shutdown()
        speechService = null
        recognizer.close()
    }

    override fun onPartialResult(hypothesis: String?) = check(hypothesis, "partial")

    override fun onResult(hypothesis: String?) = check(hypothesis, "text")

    override fun onFinalResult(hypothesis: String?) = Unit

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Wake word recognizer failed", exception)
    }

    override fun onTimeout() = Unit

    private fun check(hypothesis: String?, key: String) {
        if (triggered || hypothesis == null) return
        val text = runCatching { JSONObject(hypothesis).optString(key) }.getOrDefault("")
        if (text.contains(phrase)) {
            triggered = true
            onDetected()
        }
    }

    private companion object {
        const val TAG = "WakeWordDetector"
        const val SAMPLE_RATE = 16000f
    }
}
