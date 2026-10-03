package com.dailytodo.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

object TodoJson {
    fun encode(data: TodoData): String = JSONObject().apply {
        put("version", 1)
        put("routines", JSONArray().apply {
            data.routines.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("space", r.space.name)
                    put("title", r.title)
                    put("emoji", r.emoji)
                    put("doneDates", JSONArray(r.doneDates.sorted()))
                    put("note", r.note)
                    put("days", JSONArray(r.days.map { it.value }.sorted()))
                })
            }
        })
        put("events", JSONArray().apply {
            data.events.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("space", e.space.name)
                    put("title", e.title)
                    put("note", e.note)
                    put("times", JSONArray(e.times))
                    put("done", e.done)
                })
            }
        })
    }.toString()

    fun decode(text: String): TodoData {
        val root = JSONObject(text)
        val routines = root.optJSONArray("routines").objects().map { o ->
            Routine(
                id = o.getLong("id"),
                space = Space.valueOf(o.getString("space")),
                title = o.getString("title"),
                emoji = o.optString("emoji", "✨"),
                doneDates = o.optJSONArray("doneDates").strings().toSet(),
                note = o.optString("note", ""),
                days = o.optJSONArray("days").longs().map { DayOfWeek.of(it.toInt()) }.toSet(),
            )
        }
        val events = root.optJSONArray("events").objects().map { o ->
            PlannedEvent(
                id = o.getLong("id"),
                space = Space.valueOf(o.getString("space")),
                title = o.getString("title"),
                note = o.optString("note", ""),
                times = o.optJSONArray("times").longs(),
                done = o.optBoolean("done", false),
            )
        }
        return TodoData(routines, events)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun JSONArray?.longs(): List<Long> =
        if (this == null) emptyList() else (0 until length()).map { getLong(it) }
}
