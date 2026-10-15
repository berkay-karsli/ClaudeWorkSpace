package com.dailytodo.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dailytodo.data.PlannedEvent
import com.dailytodo.data.Routine
import com.dailytodo.data.TodoData
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sets one exact alarm per upcoming alert time of each planned event, and one per alarm time of
 * each routine (only the next occurrence; the receiver books the one after when it fires).
 */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Notifications for to-dos that don't ring like an alarm" },
        )
        // The alarm service plays the chosen sound itself, so this channel stays silent.
        manager.createNotificationChannel(
            NotificationChannel(RINGING_CHANNEL_ID, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while a to-do alarm is ringing"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    fun schedule(event: PlannedEvent) {
        if (event.done) return
        event.upcomingTimes(System.currentTimeMillis()).forEach { time ->
            setExact(time, eventIntent(event.id, time))
        }
    }

    fun cancel(event: PlannedEvent) {
        event.times.forEach { alarms.cancel(eventIntent(event.id, it)) }
    }

    fun scheduleRoutine(routine: Routine) {
        val now = LocalDateTime.now()
        routine.alarmTimes.forEach { minute ->
            val next = routine.nextAlarm(minute, now) ?: return@forEach
            setExact(next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), routineIntent(routine.id, minute))
        }
    }

    fun cancelRoutine(routine: Routine) {
        routine.alarmTimes.forEach { alarms.cancel(routineIntent(routine.id, it)) }
    }

    fun rescheduleAll(data: TodoData) {
        data.events.forEach(::schedule)
        data.routines.forEach(::scheduleRoutine)
    }

    /** Rings [alarm] again in [minutes]. */
    fun snooze(alarm: RingingAlarm, minutes: Int) {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_RING)
        alarm.writeTo(intent)
        val pending = PendingIntent.getBroadcast(
            context, "snooze:${alarm.kind}:${alarm.id}".hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setExact(System.currentTimeMillis() + minutes * 60_000L, pending)
    }

    private fun setExact(time: Long, pending: PendingIntent) {
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

    private fun eventIntent(eventId: Long, time: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_ALERT
            putExtra(ReminderReceiver.EXTRA_KIND, RingingAlarm.KIND_EVENT)
            putExtra(ReminderReceiver.EXTRA_ID, eventId)
            putExtra(ReminderReceiver.EXTRA_TIME, time)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(eventId, time), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun routineIntent(routineId: Long, minute: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_ALERT
            putExtra(ReminderReceiver.EXTRA_KIND, RingingAlarm.KIND_ROUTINE)
            putExtra(ReminderReceiver.EXTRA_ID, routineId)
            putExtra(ReminderReceiver.EXTRA_MINUTE, minute)
        }
        return PendingIntent.getBroadcast(
            context, "routine:$routineId:$minute".hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "event_alerts"
        const val RINGING_CHANNEL_ID = "alarm_ringing"

        fun requestCode(eventId: Long, time: Long): Int = (eventId * 31 + time).hashCode()
    }
}
