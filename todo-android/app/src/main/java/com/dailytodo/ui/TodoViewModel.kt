package com.dailytodo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailytodo.data.PlannedEvent
import com.dailytodo.data.Routine
import com.dailytodo.data.Space
import com.dailytodo.data.TodoData
import com.dailytodo.todoApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

class TodoViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = app.todoApp.repository
    private val scheduler = app.todoApp.scheduler

    val data: StateFlow<TodoData> = repository.data

    private val _today = MutableStateFlow(LocalDate.now())
    /** Routines renew when this rolls over to a new day. */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                refreshClock()
            }
        }
    }

    fun refreshClock() {
        _today.value = LocalDate.now()
        _now.value = System.currentTimeMillis()
    }

    // --- Daily routines ---

    fun toggleRoutine(id: Long) = repository.update { d ->
        d.copy(routines = d.routines.map { if (it.id == id) it.toggledOn(_today.value) else it })
    }

    fun saveRoutine(
        existing: Routine?,
        space: Space,
        title: String,
        emoji: String,
        note: String,
        days: Set<DayOfWeek>,
    ) = repository.update { d ->
        // All seven days is the same as "every day".
        val schedule = if (days.size == 7) emptySet() else days
        val routine = (existing ?: Routine(newId(), space, "", emoji))
            .copy(title = title.trim(), emoji = emoji, note = note.trim(), days = schedule)
        if (existing == null) d.copy(routines = d.routines + routine)
        else d.copy(routines = d.routines.map { if (it.id == routine.id) routine else it })
    }

    fun deleteRoutine(routine: Routine) = repository.update { d ->
        d.copy(routines = d.routines.filterNot { it.id == routine.id })
    }

    fun restoreRoutine(routine: Routine, index: Int) = repository.update { d ->
        val list = d.routines.toMutableList()
        list.add(index.coerceIn(0, list.size), routine)
        d.copy(routines = list)
    }

    // --- Planned events ---

    fun saveEvent(existing: PlannedEvent?, space: Space, title: String, note: String, times: List<Long>) {
        val event = (existing ?: PlannedEvent(newId(), space, "")).copy(
            title = title.trim(), note = note.trim(), times = times.distinct().sorted(),
        )
        existing?.let(scheduler::cancel)
        repository.update { d ->
            if (existing == null) d.copy(events = d.events + event)
            else d.copy(events = d.events.map { if (it.id == event.id) event else it })
        }
        scheduler.schedule(event)
    }

    fun toggleEventDone(event: PlannedEvent) {
        val updated = event.copy(done = !event.done)
        repository.update { d -> d.copy(events = d.events.map { if (it.id == event.id) updated else it }) }
        if (updated.done) scheduler.cancel(updated) else scheduler.schedule(updated)
    }

    fun deleteEvent(event: PlannedEvent) {
        scheduler.cancel(event)
        repository.update { d -> d.copy(events = d.events.filterNot { it.id == event.id }) }
    }

    fun restoreEvent(event: PlannedEvent) {
        repository.update { d -> d.copy(events = d.events + event) }
        scheduler.schedule(event)
    }

    fun routineIndex(routine: Routine) = data.value.routines.indexOfFirst { it.id == routine.id }

    private fun newId() = System.nanoTime()
}
