package com.voicemusic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.UUID

/** Speaks short confirmations so the user doesn't need to look at the phone. Main thread only. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    // Lowers the music while speaking.
    private val duck = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, this)
    private val pending = mutableMapOf<String, () -> Unit>()
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finish(utteranceId)
            override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId)
        })
        ready = true
    }

    /** Speaks [text], then calls [onDone] (right away if speech isn't available). */
    fun speak(text: String, onDone: () -> Unit = {}) {
        if (!ready) {
            onDone()
            return
        }
        val id = UUID.randomUUID().toString()
        audioManager.requestAudioFocus(duck)
        pending[id] = {
            audioManager.abandonAudioFocusRequest(duck)
            onDone()
        }
        // Safety net in case the engine never reports back.
        handler.postDelayed({ pending.remove(id)?.invoke() }, MAX_SPEECH_MS)
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finish(id)
    }

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        pending.clear()
        audioManager.abandonAudioFocusRequest(duck)
        tts.shutdown()
    }

    private fun finish(id: String) {
        handler.post { pending.remove(id)?.invoke() }
    }

    private companion object {
        const val MAX_SPEECH_MS = 8_000L
    }
}
