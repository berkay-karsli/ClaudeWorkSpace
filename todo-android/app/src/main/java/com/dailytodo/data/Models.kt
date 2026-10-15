package com.dailytodo.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

enum class Space { HOME, UNIVERSITY }

/**
 * A task that comes back on its scheduled weekdays ([days]; empty means every day).
 * It is "done" only for the dates listed in [doneDates].
 */
data class Routine(
    val id: Long,
    val space: Space,
    val title: String,
    val emoji: String,
    val doneDates: Set<String> = emptySet(),
    val note: String = "",
    val days: Set<DayOfWeek> = emptySet(),
    /** Alarm times as minutes after midnight; they go off on the days the routine is due. */
    val alarmTimes: List<Int> = emptyList(),
    /** Ring like an alarm (true) or just post a notification (false). */
    val ring: Boolean = false,
    /** See [com.dailytodo.alarm.Sounds]: "" = default alarm sound, "silent", or a sound URI. */
    val sound: String = "",
) {
    val isEveryDay: Boolean get() = days.isEmpty() || days.size == 7

    fun isScheduledOn(date: LocalDate): Boolean = isEveryDay || date.dayOfWeek in days

    fun isDoneOn(date: LocalDate): Boolean = date.toString() in doneDates

    fun toggledOn(date: LocalDate): Routine {
        val key = date.toString()
        val dates = if (key in doneDates) doneDates - key else doneDates + key
        // Keep only the recent history needed for streaks.
        val cutoff = date.minusDays(HISTORY_DAYS).toString()
        return copy(doneDates = dates.filter { it >= cutoff }.toSet())
    }

    /**
     * Scheduled days done in a row, ending today (or the previous scheduled day, if today isn't
     * done yet). Days the routine isn't scheduled on don't break the streak.
     */
    fun streak(today: LocalDate): Int {
        var day = if (isScheduledOn(today) && !isDoneOn(today)) today.minusDays(1) else today
        var count = 0
        repeat(HISTORY_DAYS.toInt()) {
            if (isScheduledOn(day)) {
                if (!isDoneOn(day)) return count
                count++
            }
            day = day.minusDays(1)
        }
        return count
    }

    /** The next time [minuteOfDay] falls on a day this routine is due, strictly after [now]. */
    fun nextAlarm(minuteOfDay: Int, now: LocalDateTime): LocalDateTime? {
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            val candidate = date.atTime(minuteOfDay / 60, minuteOfDay % 60)
            if (candidate.isAfter(now) && isScheduledOn(date)) return candidate
        }
        return null
    }

    companion object {
        const val HISTORY_DAYS = 366L
    }
}

/** A one-off event with one or more alert times (epoch millis). */
data class PlannedEvent(
    val id: Long,
    val space: Space,
    val title: String,
    val note: String = "",
    val times: List<Long> = emptyList(),
    val done: Boolean = false,
    /** Ring like an alarm (true) or just post a notification (false). */
    val ring: Boolean = false,
    /** See [com.dailytodo.alarm.Sounds]. */
    val sound: String = "",
) {
    fun nextTime(now: Long): Long? = times.filter { it > now }.minOrNull()

    fun upcomingTimes(now: Long): List<Long> = times.filter { it > now }.sorted()

    /** The time to show on the card: the next one, else the latest past one. */
    fun displayTime(now: Long): Long? = nextTime(now) ?: times.maxOrNull()

    fun isActive(now: Long): Boolean = !done && nextTime(now) != null
}

/**
 * A saved "alarm ladder" for the clock app. [steps] are times on a timeline (minutes, e.g. 00:00,
 * 00:10, 00:20, 00:45, 02:00); the latest step lines up with the [deadline] and the others keep
 * their distance from it. Steps 0/10/20/45/120 with a 10:00 deadline give 08:00, 08:10, 08:20,
 * 08:45 and 10:00.
 */
data class AlarmPreset(
    val id: Long,
    val name: String,
    val steps: List<Int> = listOf(0),
    /** Minutes after midnight. */
    val deadline: Int = 8 * 60,
    val sound: String = "",
) {
    val sortedSteps: List<Int> get() = steps.distinct().sorted().ifEmpty { listOf(0) }

    /** How long before the deadline a step rings, in minutes. */
    fun minutesBefore(step: Int): Int = sortedSteps.last() - step

    /** The clock times (minutes after midnight) of every alarm, earliest first. */
    fun alarmTimes(): List<Int> = sortedSteps.map { Math.floorMod(deadline - minutesBefore(it), MINUTES_PER_DAY) }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        fun example() = AlarmPreset(1, "Wake-up ladder", listOf(0, 10, 20, 45, 120), deadline = 10 * 60)
    }
}

data class TodoData(
    val routines: List<Routine> = emptyList(),
    val events: List<PlannedEvent> = emptyList(),
    val presets: List<AlarmPreset> = emptyList(),
) {
    fun routinesIn(space: Space) = routines.filter { it.space == space }

    fun routinesDueOn(space: Space, date: LocalDate) = routinesIn(space).filter { it.isScheduledOn(date) }

    /** Upcoming events first (soonest on top), then undated, then past/done ones (newest on top). */
    fun eventsIn(space: Space, now: Long): List<PlannedEvent> {
        val (active, rest) = events.filter { it.space == space }.partition { it.isActive(now) }
        val (undated, finished) = rest.partition { !it.done && it.times.isEmpty() }
        return active.sortedBy { it.nextTime(now) } +
            undated +
            finished.sortedByDescending { it.times.maxOrNull() ?: 0L }
    }
}
