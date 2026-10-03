package com.dailytodo.ui

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()
private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

fun LocalDateTime.toEpochMillis(): Long = atZone(zone).toInstant().toEpochMilli()

fun formatTime(millis: Long): String = millis.toLocalDateTime().format(timeFormat)

fun formatDay(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days == -1L -> "Yesterday"
        days in 2..6 -> date.format(DateTimeFormatter.ofPattern("EEEE"))
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

/** e.g. "Today · 14:30", "Fri, 3 Oct · 09:00". */
fun formatAlert(millis: Long, today: LocalDate): String {
    val dt = millis.toLocalDateTime()
    return "${formatDay(dt.toLocalDate(), today)} · ${dt.format(timeFormat)}"
}

/** A short countdown for the soonest alert: "in 25 min", "in 3 h", "in 2 days". */
fun countdown(millis: Long, now: Long): String {
    val minutes = (millis - now) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "in $minutes min"
        minutes < 48 * 60 -> "in ${(minutes + 30) / 60} h"
        else -> "in ${minutes / (24 * 60)} days"
    }
}

fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning ☀️"
    in 12..17 -> "Good afternoon 🌤️"
    in 18..21 -> "Good evening 🌇"
    else -> "Good night 🌙"
}

val headerDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

/** Monday-first week, in the phone's language. */
val weekOrder: List<DayOfWeek> = DayOfWeek.values().toList()

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

fun DayOfWeek.letter(): String = getDisplayName(TextStyle.NARROW, Locale.getDefault())

/** "Every day", "Weekdays", "Weekends" or e.g. "Mon · Wed · Fri". */
fun scheduleLabel(days: Set<DayOfWeek>): String = when {
    days.isEmpty() || days.size == 7 -> "Every day"
    days == weekdays -> "Weekdays"
    days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Weekends"
    else -> weekOrder.filter { it in days }.joinToString(" · ") { it.shortName() }
}
