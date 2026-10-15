package com.dailytodo

import com.dailytodo.alarm.ClockApp
import com.dailytodo.data.AlarmPreset
import com.dailytodo.data.PlannedEvent
import com.dailytodo.data.Routine
import com.dailytodo.data.Space
import com.dailytodo.data.TodoData
import com.dailytodo.data.TodoJson
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class AlarmsTest {
    private fun hm(h: Int, m: Int) = h * 60 + m

    @Test
    fun ladderLinesUpTheLastStepWithTheDeadline() {
        // The example: steps 00:00, 00:10, 00:20, 00:45, 02:00 with a 10:00 deadline.
        val ladder = AlarmPreset(1, "Wake up", listOf(0, 10, 20, 45, 120), deadline = hm(10, 0))
        assertEquals(
            listOf(hm(8, 0), hm(8, 10), hm(8, 20), hm(8, 45), hm(10, 0)),
            ladder.alarmTimes(),
        )
        assertEquals(120, ladder.minutesBefore(0))
        assertEquals(0, ladder.minutesBefore(120))
    }

    @Test
    fun ladderStepsAreSortedAndDeduplicated() {
        val ladder = AlarmPreset(1, "x", listOf(30, 0, 30, 15), deadline = hm(7, 0))
        assertEquals(listOf(hm(6, 30), hm(6, 45), hm(7, 0)), ladder.alarmTimes())
    }

    @Test
    fun ladderWrapsPastMidnight() {
        val ladder = AlarmPreset(1, "x", listOf(0, 90), deadline = hm(0, 30))
        assertEquals(listOf(hm(23, 0), hm(0, 30)), ladder.alarmTimes())
    }

    @Test
    fun emptyLadderIsJustTheDeadline() {
        assertEquals(listOf(hm(9, 0)), AlarmPreset(1, "x", emptyList(), deadline = hm(9, 0)).alarmTimes())
    }

    @Test
    fun durationLabels() {
        assertEquals("45 min", ClockApp.durationLabel(45))
        assertEquals("2 h", ClockApp.durationLabel(120))
        assertEquals("1 h 15 min", ClockApp.durationLabel(75))
    }

    @Test
    fun routineAlarmRingsLaterTodayOrNextDueDay() {
        val tuesdayMorning = LocalDateTime.of(2026, 9, 29, 7, 0) // a Tuesday
        val daily = Routine(1, Space.HOME, "Bed", "🛏️")
        assertEquals(LocalDateTime.of(2026, 9, 29, 8, 0), daily.nextAlarm(hm(8, 0), tuesdayMorning))
        // 06:00 has passed today, so tomorrow.
        assertEquals(LocalDateTime.of(2026, 9, 30, 6, 0), daily.nextAlarm(hm(6, 0), tuesdayMorning))
        // Exactly now counts as passed.
        assertEquals(LocalDateTime.of(2026, 9, 30, 7, 0), daily.nextAlarm(hm(7, 0), tuesdayMorning))

        val fridays = daily.copy(days = setOf(DayOfWeek.FRIDAY))
        assertEquals(LocalDateTime.of(2026, 10, 2, 8, 0), fridays.nextAlarm(hm(8, 0), tuesdayMorning))
    }

    @Test
    fun weeklyRoutineAlarmWaitsAWholeWeek() {
        val mondays = Routine(1, Space.HOME, "x", "x", days = setOf(DayOfWeek.MONDAY))
        val mondayLate = LocalDateTime.of(2026, 9, 28, 23, 0)
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 0), mondays.nextAlarm(hm(8, 0), mondayLate))
    }

    @Test
    fun alarmSettingsAndLaddersAreSaved() {
        val data = TodoData(
            routines = listOf(Routine(1, Space.HOME, "Bed", "🛏️", alarmTimes = listOf(hm(7, 30)), ring = true, sound = "content://media/internal/audio/media/12")),
            events = listOf(PlannedEvent(2, Space.UNIVERSITY, "Exam", times = listOf(5L), ring = true, sound = "silent")),
            presets = listOf(AlarmPreset(3, "Exam day", listOf(0, 15, 60), deadline = hm(9, 0), sound = "silent")),
        )
        assertEquals(data, TodoJson.decode(TodoJson.encode(data)))
    }

    @Test
    fun olderSavesGetTheExampleLadder() {
        val old = """{"routines":[],"events":[{"id":2,"space":"HOME","title":"Bills","times":[5],"done":false}]}"""
        val data = TodoJson.decode(old)
        assertEquals(listOf(AlarmPreset.example()), data.presets)
        // Old events keep working as quiet notifications.
        assertEquals(false, data.events.single().ring)
    }
}
