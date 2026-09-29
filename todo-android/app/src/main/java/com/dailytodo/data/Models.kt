package com.dailytodo.data

import java.time.LocalDate

enum class Space { HOME, UNIVERSITY }

/** A task that comes back every day. It is "done" only for the days listed in [doneDates]. */
data class Routine(
    val id: Long,
    val space: Space,
    val title: String,
    val emoji: String,
    val doneDates: Set<String> = emptySet(),
) {
    fun isDoneOn(date: LocalDate): Boolean = date.toString() in doneDates

    fun toggledOn(date: LocalDate): Routine {
        val key = date.toString()
        val dates = if (key in doneDates) doneDates - key else doneDates + key
        // Keep only the recent history needed for streaks.
        val cutoff = date.minusDays(HISTORY_DAYS).toString()
        return copy(doneDates = dates.filter { it >= cutoff }.toSet())
    }

    /** Consecutive days done, ending today (or yesterday, if today isn't done yet). */
    fun streak(today: LocalDate): Int {
        var day = if (isDoneOn(today)) today else today.minusDays(1)
        var count = 0
        while (isDoneOn(day)) {
            count++
            day = day.minusDays(1)
        }
        return count
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
) {
    fun nextTime(now: Long): Long? = times.filter { it > now }.minOrNull()

    fun upcomingTimes(now: Long): List<Long> = times.filter { it > now }.sorted()

    /** The time to show on the card: the next one, else the latest past one. */
    fun displayTime(now: Long): Long? = nextTime(now) ?: times.maxOrNull()

    fun isActive(now: Long): Boolean = !done && nextTime(now) != null
}

data class TodoData(
    val routines: List<Routine> = emptyList(),
    val events: List<PlannedEvent> = emptyList(),
) {
    fun routinesIn(space: Space) = routines.filter { it.space == space }

    /** Upcoming events first (soonest on top), then undated, then past/done ones (newest on top). */
    fun eventsIn(space: Space, now: Long): List<PlannedEvent> {
        val (active, rest) = events.filter { it.space == space }.partition { it.isActive(now) }
        val (undated, finished) = rest.partition { !it.done && it.times.isEmpty() }
        return active.sortedBy { it.nextTime(now) } +
            undated +
            finished.sortedByDescending { it.times.maxOrNull() ?: 0L }
    }
}
