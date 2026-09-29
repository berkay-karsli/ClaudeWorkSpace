package com.dailytodo.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

/** Holds all to-dos in memory and mirrors them to a small JSON file. */
class TodoRepository(context: Context) {
    private val file = File(context.filesDir, "todos.json")
    private val writer = Executors.newSingleThreadExecutor()
    private val _data = MutableStateFlow(load())
    val data: StateFlow<TodoData> = _data.asStateFlow()

    @Synchronized
    fun update(transform: (TodoData) -> TodoData) {
        val next = transform(_data.value)
        _data.value = next
        val json = TodoJson.encode(next)
        writer.execute {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json)
            tmp.renameTo(file)
        }
    }

    fun event(id: Long): PlannedEvent? = _data.value.events.firstOrNull { it.id == id }

    private fun load(): TodoData {
        if (!file.exists()) return starterData()
        return try {
            TodoJson.decode(file.readText())
        } catch (e: Exception) {
            Log.e("TodoRepository", "Could not read saved to-dos", e)
            TodoData()
        }
    }

    /** A few examples on first launch so the app doesn't open empty. */
    private fun starterData(): TodoData {
        var id = System.currentTimeMillis()
        fun r(space: Space, emoji: String, title: String) = Routine(id++, space, title, emoji)
        return TodoData(
            routines = listOf(
                r(Space.HOME, "🛏️", "Make the bed"),
                r(Space.HOME, "💧", "Water the plants"),
                r(Space.HOME, "🍽️", "Wash the dishes"),
                r(Space.UNIVERSITY, "📚", "Review today's lecture notes"),
                r(Space.UNIVERSITY, "✉️", "Check university email"),
            ),
        )
    }
}
