package com.dailytodo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailytodo.data.AlarmPreset
import com.dailytodo.data.PlannedEvent
import com.dailytodo.data.Routine
import com.dailytodo.data.TodoData
import com.dailytodo.todoApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    /** Saves a routine from the editor; an id of 0 means it's new. */
    fun saveRoutine(draft: Routine) {
        val routine = draft.copy(
            id = if (draft.id == 0L) newId() else draft.id,
            title = draft.title.trim(),
            note = draft.note.trim(),
            // All seven days is the same as "every day".
            days = if (draft.days.size == 7) emptySet() else draft.days,
            alarmTimes = draft.alarmTimes.distinct().sorted(),
        )
        val existing = repository.routine(routine.id)
        existing?.let(scheduler::cancelRoutine)
        repository.update { d ->
            if (existing == null) d.copy(routines = d.routines + routine)
            else d.copy(routines = d.routines.map { if (it.id == routine.id) routine else it })
        }
        scheduler.scheduleRoutine(routine)
    }

    fun deleteRoutine(routine: Routine) {
        scheduler.cancelRoutine(routine)
        repository.update { d -> d.copy(routines = d.routines.filterNot { it.id == routine.id }) }
    }

    fun restoreRoutine(routine: Routine, index: Int) {
        repository.update { d ->
            val list = d.routines.toMutableList()
            list.add(index.coerceIn(0, list.size), routine)
            d.copy(routines = list)
        }
        scheduler.scheduleRoutine(routine)
    }

    // --- Planned events ---

    /** Saves an event from the editor; an id of 0 means it's new. */
    fun saveEvent(draft: PlannedEvent) {
        val event = draft.copy(
            id = if (draft.id == 0L) newId() else draft.id,
            title = draft.title.trim(),
            note = draft.note.trim(),
            times = draft.times.distinct().sorted(),
        )
        val existing = repository.event(event.id)
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

    // --- Alarm ladder presets ---

    fun updatePreset(id: Long, change: (AlarmPreset) -> AlarmPreset) = repository.update { d ->
        d.copy(presets = d.presets.map { if (it.id == id) change(it) else it })
    }

    /** Adds a new preset and returns its id. */
    fun addPreset(): Long {
        val preset = AlarmPreset(newId(), "New ladder", steps = listOf(0, 10, 20), deadline = 8 * 60)
        repository.update { d -> d.copy(presets = d.presets + preset) }
        return preset.id
    }

    fun deletePreset(preset: AlarmPreset) = repository.update { d ->
        d.copy(presets = d.presets.filterNot { it.id == preset.id })
    }

    fun restorePreset(preset: AlarmPreset) = repository.update { d -> d.copy(presets = d.presets + preset) }

    fun routineIndex(routine: Routine) = data.value.routines.indexOfFirst { it.id == routine.id }

    private fun newId() = System.nanoTime()
}
