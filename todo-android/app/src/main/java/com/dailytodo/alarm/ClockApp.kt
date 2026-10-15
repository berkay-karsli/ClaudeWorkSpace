package com.dailytodo.alarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.dailytodo.data.AlarmPreset
import kotlinx.coroutines.delay

/** Hands alarms to the phone's own clock app (Google Clock, Samsung Clock, ...). */
object ClockApp {
    sealed interface Result {
        data class Done(val count: Int) : Result
        data object NoClockApp : Result
    }

    /**
     * Creates one clock-app alarm per alarm of [preset], earliest first. Each request opens the
     * clock app briefly, so they're sent one after another with a short pause in between.
     */
    suspend fun setAlarms(context: Context, preset: AlarmPreset, onProgress: (Int) -> Unit): Result {
        val offsets = preset.offsets
        offsets.forEachIndexed { index, before ->
            onProgress(index + 1)
            val minute = preset.timeOf(before)
            val label = if (before == 0) "${preset.name} · deadline" else "${preset.name} · ${durationLabel(before)} left"
            try {
                context.startActivity(setAlarmIntent(minute / 60, minute % 60, label, preset.sound))
            } catch (e: ActivityNotFoundException) {
                return Result.NoClockApp
            }
            delay(900)
        }
        return Result.Done(offsets.size)
    }

    private fun setAlarmIntent(hour: Int, minute: Int, label: String, sound: String) =
        Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            when {
                sound == Sounds.SILENT -> putExtra(AlarmClock.EXTRA_RINGTONE, AlarmClock.VALUE_RINGTONE_SILENT)
                sound.isNotBlank() -> putExtra(AlarmClock.EXTRA_RINGTONE, sound)
            }
        }

    /** 135 → "2 h 15 min", 120 → "2 h", 45 → "45 min". */
    fun durationLabel(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }
}
