package com.voicemusic

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * The app's "sleep" mode: listens to the microphone fully offline and ignores everything except
 * the wake phrase. Calls [onDetected] when the phrase is heard.
 *
 * To keep ordinary conversation from waking it:
 * - Recognition is limited to the wake phrase plus a set of everyday decoy words, so speech that
 *   merely sounds similar is matched to a decoy instead of being forced onto the wake phrase.
 * - It only reacts to a finished utterance (after a short pause), never to a half-heard guess.
 * - Each word of the phrase must be recognized with at least [minConfidence].
 */
class WakeWordDetector(
    model: Model,
    wakePhrase: String,
    private val minConfidence: Double,
    private val onDetected: () -> Unit,
) : RecognitionListener {

    private val phraseWords = Prefs.normalizePhrase(wakePhrase).split(' ')
    private val recognizer = Recognizer(model, SAMPLE_RATE, grammar(phraseWords)).apply { setWords(true) }
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

    override fun onPartialResult(hypothesis: String?) = Unit

    override fun onResult(hypothesis: String?) {
        if (triggered || hypothesis == null) return
        val words = runCatching { JSONObject(hypothesis).optJSONArray("result") }.getOrNull() ?: return
        if (containsWakePhrase(words)) {
            triggered = true
            onDetected()
        }
    }

    override fun onFinalResult(hypothesis: String?) = Unit

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Wake word recognizer failed", exception)
    }

    override fun onTimeout() = Unit

    /** True if the phrase's words appear consecutively, each with enough confidence. */
    private fun containsWakePhrase(result: JSONArray): Boolean {
        val words = (0 until result.length()).map { result.getJSONObject(it) }
        for (start in 0..words.size - phraseWords.size) {
            val matches = phraseWords.indices.all { i ->
                val word = words[start + i]
                word.optString("word") == phraseWords[i] && word.optDouble("conf", 0.0) >= minConfidence
            }
            if (matches) return true
        }
        return false
    }

    private companion object {
        const val TAG = "WakeWordDetector"
        const val SAMPLE_RATE = 16000f

        /** Common words that absorb everyday speech. Words missing from the model are skipped by Vosk. */
        val DECOYS = listOf(
            "hi", "hello", "hey", "okay", "yes", "yeah", "no", "not", "what", "why", "how", "who", "where",
            "when", "that", "this", "the", "a", "and", "but", "so", "i", "you", "we", "they", "he", "she",
            "it", "is", "are", "was", "be", "do", "go", "going", "get", "got", "know", "think", "like",
            "want", "look", "right", "left", "here", "there", "now", "then", "just", "really", "good",
            "great", "man", "come", "on", "in", "at", "to", "of", "for", "with", "my", "your", "me",
            "us", "them", "one", "two", "time", "way", "day", "car", "road", "turn", "stop", "wait",
            "play", "song", "news", "muse", "musical", "magic", "mister", "may", "make", "made", "much",
            "many", "move", "mix", "mean", "music's", "amazing", "missing", "moving", "honey", "heavy",
            "happy", "hate", "hay", "they're", "say", "said", "see", "some", "sick", "kids", "keep",
        )

        fun grammar(phraseWords: List<String>): String {
            // The phrase's single words are included too, so "hey" or "music" alone don't count.
            val entries = linkedSetOf(phraseWords.joinToString(" ")) + DECOYS + phraseWords + "[unk]"
            return JSONArray(entries.toList()).toString()
        }
    }
}
