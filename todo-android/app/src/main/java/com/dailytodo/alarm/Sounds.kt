package com.dailytodo.alarm

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings

/**
 * Alarm sounds are stored as a string: "" means the phone's default alarm sound, [SILENT] means
 * vibrate only, anything else is a sound URI picked in the system sound picker.
 */
object Sounds {
    const val SILENT = "silent"

    fun uriOf(sound: String): Uri? = when {
        sound == SILENT -> null
        sound.isBlank() -> Settings.System.DEFAULT_ALARM_ALERT_URI
        else -> Uri.parse(sound)
    }

    fun pickerIntent(current: String, title: String = "Alarm sound"): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uriOf(current))
        }

    /** Converts the picker's answer (null = "Silent") back to the stored form. */
    fun fromPicked(uri: Uri?): String = when (uri) {
        null -> SILENT
        Settings.System.DEFAULT_ALARM_ALERT_URI -> ""
        else -> uri.toString()
    }

    fun label(context: Context, sound: String): String = when {
        sound == SILENT -> "Silent · vibrate only"
        sound.isBlank() -> "Default alarm sound"
        else -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(sound))?.getTitle(context) }
            .getOrNull() ?: "Custom sound"
    }
}
