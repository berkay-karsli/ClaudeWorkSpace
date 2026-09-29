package com.voicemusic

import android.content.Context
import java.util.Locale

object Prefs {
    const val DEFAULT_WAKE_PHRASE = "hey music"

    private const val FILE = "settings"
    private const val KEY_WAKE_PHRASE = "wake_phrase"
    private const val KEY_SENSITIVITY = "wake_sensitivity"

    /** How easily the wake phrase triggers. Lower = fewer accidental wake-ups from conversation. */
    enum class Sensitivity(val minConfidence: Double) { LOW(0.9), NORMAL(0.75), HIGH(0.55) }

    fun sensitivity(context: Context): Sensitivity =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_SENSITIVITY, null)
            ?.let { name -> Sensitivity.entries.firstOrNull { it.name == name } }
            ?: Sensitivity.NORMAL

    fun setSensitivity(context: Context, sensitivity: Sensitivity) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_SENSITIVITY, sensitivity.name)
            .apply()
    }

    fun wakePhrase(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_WAKE_PHRASE, null)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_WAKE_PHRASE

    fun setWakePhrase(context: Context, phrase: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_WAKE_PHRASE, normalizePhrase(phrase))
            .apply()
    }

    fun normalizePhrase(phrase: String): String =
        phrase.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
