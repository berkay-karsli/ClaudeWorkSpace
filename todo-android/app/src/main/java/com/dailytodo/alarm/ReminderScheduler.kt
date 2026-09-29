package com.dailytodo.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dailytodo.data.PlannedEvent

/** Sets one exact alarm per upcoming alert time of each planned event. */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Planned event alerts", NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Reminders for your planned events" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(event: PlannedEvent) {
        if (event.done) return
        val now = System.currentTimeMillis()
        event.upcomingTimes(now).forEach { time ->
            val pending = pendingIntent(event.id, time)
            try {
                if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
                }
            } catch (e: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
            }
        }
    }

    fun cancel(event: PlannedEvent) {
        event.times.forEach { alarms.cancel(pendingIntent(event.id, it)) }
    }

    fun rescheduleAll(events: List<PlannedEvent>) = events.forEach(::schedule)

    private fun pendingIntent(eventId: Long, time: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_ALERT
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderReceiver.EXTRA_TIME, time)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(eventId, time), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "event_alerts"

        fun requestCode(eventId: Long, time: Long): Int = (eventId * 31 + time).hashCode()
    }
}
