package com.dailytodo

import android.app.Application
import com.dailytodo.alarm.ReminderScheduler
import com.dailytodo.data.TodoRepository

class TodoApp : Application() {
    lateinit var repository: TodoRepository
        private set
    lateinit var scheduler: ReminderScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        repository = TodoRepository(this)
        scheduler = ReminderScheduler(this)
        scheduler.createChannels()
        scheduler.rescheduleAll(repository.data.value)
    }
}

val android.content.Context.todoApp: TodoApp get() = applicationContext as TodoApp
