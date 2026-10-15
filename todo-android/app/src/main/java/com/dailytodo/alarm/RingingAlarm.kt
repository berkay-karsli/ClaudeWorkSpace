package com.dailytodo.alarm

import android.content.Intent
import com.dailytodo.data.Space
import kotlinx.coroutines.flow.MutableStateFlow

/** What the alarm screen and ringing notification show, passed around in intent extras. */
data class RingingAlarm(
    val kind: String,
    val id: Long,
    val title: String,
    val emoji: String,
    val space: Space,
    val sound: String,
) {
    fun writeTo(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_KIND, kind)
        putExtra(EXTRA_ID, id)
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_EMOJI, emoji)
        putExtra(EXTRA_SPACE, space.name)
        putExtra(EXTRA_SOUND, sound)
    }

    companion object {
        const val KIND_EVENT = "event"
        const val KIND_ROUTINE = "routine"

        private const val EXTRA_KIND = "ring_kind"
        private const val EXTRA_ID = "ring_id"
        private const val EXTRA_TITLE = "ring_title"
        private const val EXTRA_EMOJI = "ring_emoji"
        private const val EXTRA_SPACE = "ring_space"
        private const val EXTRA_SOUND = "ring_sound"

        fun from(intent: Intent?): RingingAlarm? {
            if (intent == null || !intent.hasExtra(EXTRA_ID)) return null
            return RingingAlarm(
                kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_EVENT,
                id = intent.getLongExtra(EXTRA_ID, -1),
                title = intent.getStringExtra(EXTRA_TITLE) ?: "",
                emoji = intent.getStringExtra(EXTRA_EMOJI) ?: "⏰",
                space = runCatching { Space.valueOf(intent.getStringExtra(EXTRA_SPACE)!!) }.getOrDefault(Space.HOME),
                sound = intent.getStringExtra(EXTRA_SOUND) ?: "",
            )
        }
    }
}

/** The alarm that is ringing right now, if any. The alarm screen closes when this becomes null. */
object AlarmState {
    val ringing = MutableStateFlow<RingingAlarm?>(null)
}
