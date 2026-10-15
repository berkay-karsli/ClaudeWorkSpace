package com.dailytodo.alarm

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dailytodo.MainActivity
import com.dailytodo.R
import com.dailytodo.data.Space
import com.dailytodo.todoApp
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.todoApp
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: RingingAlarm.KIND_EVENT
        when (intent.action) {
            ACTION_ALERT -> when (kind) {
                RingingAlarm.KIND_ROUTINE -> {
                    val routine = app.repository.routine(id) ?: return
                    app.scheduler.scheduleRoutine(routine) // book the next occurrence
                    val today = LocalDate.now()
                    if (!routine.isScheduledOn(today) || routine.isDoneOn(today)) return
                    val alarm = RingingAlarm(kind, id, routine.title, routine.emoji, routine.space, routine.sound)
                    if (!routine.ring || !AlarmService.start(context, alarm)) {
                        notify(context, alarm, note = "", time = System.currentTimeMillis())
                    }
                }
                else -> {
                    val event = app.repository.event(id) ?: return
                    if (event.done) return
                    val alarm = RingingAlarm(kind, id, event.title, "🗓️", event.space, event.sound)
                    if (!event.ring || !AlarmService.start(context, alarm)) {
                        notify(context, alarm, event.note, intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis()))
                    }
                }
            }
            // A snoozed alarm coming back.
            ACTION_RING -> RingingAlarm.from(intent)?.let { alarm ->
                if (!AlarmService.start(context, alarm)) notify(context, alarm, "", System.currentTimeMillis())
            }
            ACTION_DONE -> {
                when (kind) {
                    RingingAlarm.KIND_ROUTINE -> app.repository.markRoutineDone(id, LocalDate.now())
                    else -> {
                        app.repository.markEventDone(id)
                        app.repository.event(id)?.let(app.scheduler::cancel)
                    }
                }
                context.getSystemService(NotificationManager::class.java).cancel(notificationId(kind, id))
            }
        }
    }

    private fun notify(context: Context, alarm: RingingAlarm, note: String, time: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notificationId = notificationId(alarm.kind, alarm.id)
        val openApp = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_SPACE, alarm.space.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markDone = PendingIntent.getBroadcast(
            context, notificationId,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_DONE
                putExtra(EXTRA_KIND, alarm.kind)
                putExtra(EXTRA_ID, alarm.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val label = if (alarm.space == Space.HOME) "🏡 Home" else "🎓 University"
        val whenText = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
        val text = if (note.isBlank()) "$label · $whenText" else "$label · $whenText — $note"

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(if (alarm.space == Space.HOME) 0xFFE8735A.toInt() else 0xFF5B6CF0.toInt())
            .setContentTitle("${alarm.emoji} ${alarm.title}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(time)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_notification, "Mark done", markDone)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val ACTION_ALERT = "com.dailytodo.ALERT"
        const val ACTION_RING = "com.dailytodo.RING"
        const val ACTION_DONE = "com.dailytodo.MARK_DONE"
        const val EXTRA_KIND = "kind"
        // Kept as "event_id" so alarms booked by older versions still resolve.
        const val EXTRA_ID = "event_id"
        const val EXTRA_TIME = "time"
        const val EXTRA_MINUTE = "minute"

        fun notificationId(kind: String, id: Long): Int = "$kind:$id".hashCode()
    }
}
