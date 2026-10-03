package com.dailytodo

import com.dailytodo.data.PlannedEvent
import com.dailytodo.data.Routine
import com.dailytodo.data.Space
import com.dailytodo.data.TodoData
import com.dailytodo.data.TodoJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ModelsTest {
    private val today = LocalDate.of(2026, 9, 29)
    private val routine = Routine(1, Space.HOME, "Make the bed", "🛏️")

    @Test
    fun routineIsOnlyDoneForTheDayItWasTicked() {
        val done = routine.toggledOn(today)
        assertTrue(done.isDoneOn(today))
        // Next morning it's fresh again.
        assertFalse(done.isDoneOn(today.plusDays(1)))
    }

    @Test
    fun togglingTwiceUndoes() {
        assertFalse(routine.toggledOn(today).toggledOn(today).isDoneOn(today))
    }

    @Test
    fun streakCountsConsecutiveDays() {
        val r = routine
            .toggledOn(today.minusDays(3))
            .toggledOn(today.minusDays(2))
            .toggledOn(today.minusDays(1))
        // Today not done yet: the streak up to yesterday still counts.
        assertEquals(3, r.streak(today))
        assertEquals(4, r.toggledOn(today).streak(today))
        // A missed day breaks it.
        assertEquals(0, r.streak(today.plusDays(2)))
    }

    @Test
    fun oldHistoryIsTrimmed() {
        val r = routine.toggledOn(today.minusDays(400)).toggledOn(today)
        assertEquals(setOf(today.toString()), r.doneDates)
    }

    @Test
    fun eventNextTimeSkipsPastAlerts() {
        val e = PlannedEvent(1, Space.UNIVERSITY, "Exam", times = listOf(100L, 300L, 200L))
        assertEquals(200L, e.nextTime(150))
        assertNull(e.nextTime(300))
        assertEquals(300L, e.displayTime(1000))
        assertFalse(e.isActive(1000))
    }

    @Test
    fun eventsAreSortedUpcomingFirst() {
        val now = 1_000L
        val data = TodoData(
            events = listOf(
                PlannedEvent(1, Space.HOME, "past", times = listOf(500)),
                PlannedEvent(2, Space.HOME, "later", times = listOf(5_000)),
                PlannedEvent(3, Space.HOME, "soon", times = listOf(2_000)),
                PlannedEvent(4, Space.HOME, "undated"),
                PlannedEvent(5, Space.UNIVERSITY, "other space", times = listOf(1_500)),
                PlannedEvent(6, Space.HOME, "done", times = listOf(3_000), done = true),
            ),
        )
        assertEquals(
            listOf("soon", "later", "undated", "done", "past"),
            data.eventsIn(Space.HOME, now).map { it.title },
        )
    }

    @Test
    fun jsonRoundTrip() {
        val data = TodoData(
            routines = listOf(routine.toggledOn(today)),
            events = listOf(PlannedEvent(7, Space.UNIVERSITY, "Lab \"report\"", "Room 4", listOf(1L, 2L), done = true)),
        )
        assertEquals(data, TodoJson.decode(TodoJson.encode(data)))
    }

    @Test
    fun weekdayRoutineIsOnlyDueOnItsDays() {
        // 2026-09-29 is a Tuesday.
        val r = routine.copy(days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertFalse(r.isScheduledOn(today))
        assertTrue(r.isScheduledOn(today.plusDays(1)))
        assertTrue(routine.isScheduledOn(today))
        val data = TodoData(routines = listOf(routine, r))
        assertEquals(listOf(routine), data.routinesDueOn(Space.HOME, today))
    }

    @Test
    fun streakSkipsDaysTheRoutineIsNotDue() {
        // Mon/Wed/Fri routine, done on the last three of those days.
        val r = routine.copy(days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
            .toggledOn(LocalDate.of(2026, 9, 23)) // Wed
            .toggledOn(LocalDate.of(2026, 9, 25)) // Fri
            .toggledOn(LocalDate.of(2026, 9, 28)) // Mon
        assertEquals(3, r.streak(today)) // Tuesday: not due, streak holds
        assertEquals(3, r.streak(today.plusDays(1))) // Wednesday, not ticked yet
        assertEquals(0, r.streak(today.plusDays(2))) // Thursday: Wednesday was missed
    }

    @Test
    fun jsonKeepsNotesAndDays() {
        val r = routine.copy(note = "Fresh sheets on Sunday\nline two", days = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY))
        assertEquals(TodoData(listOf(r)), TodoJson.decode(TodoJson.encode(TodoData(listOf(r)))))
    }

    @Test
    fun oldSavesWithoutNotesOrDaysStillLoad() {
        val old = """{"routines":[{"id":1,"space":"HOME","title":"Bed","emoji":"🛏️","doneDates":[]}],"events":[]}"""
        val r = TodoJson.decode(old).routines.single()
        assertEquals("", r.note)
        assertTrue(r.isEveryDay)
    }
}
