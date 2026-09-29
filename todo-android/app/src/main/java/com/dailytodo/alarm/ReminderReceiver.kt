package com.dailytodo.alarm

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dailytodo.MainActivity
import com.dailytodo.R
import com.dailytodo.data.Space
import com.dailytodo.todoApp
import java.text.DateFormat
import java.util.Date

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1)
        val app = context.todoApp
        when (intent.action) {
            ACTION_ALERT -> {
                val event = app.repository.event(eventId) ?: return
                if (event.done) return
                val time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
                notify(context, eventId, event.title, event.note, event.space, time)
            }
            ACTION_DONE -> {
                app.repository.update { data ->
                    data.copy(events = data.events.map { if (it.id == eventId) it.copy(done = true) else it })
                }
                app.repository.event(eventId)?.let(app.scheduler::cancel)
                context.getSystemService(NotificationManager::class.java).cancel(eventId.hashCode())
            }
        }
    }

    private fun notify(context: Context, eventId: Long, title: String, note: String, space: Space, time: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33
        ) return

        val openApp = PendingIntent.getActivity(
            context, eventId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_SPACE, space.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markDone = PendingIntent.getBroadcast(
            context, eventId.hashCode(),
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_DONE
                putExtra(EXTRA_EVENT_ID, eventId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val label = if (space == Space.HOME) "🏡 Home" else "🎓 University"
        val whenText = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
        val text = if (note.isBlank()) "$label · $whenText" else "$label · $whenText — $note"

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(if (space == Space.HOME) 0xFFE8735A.toInt() else 0xFF5B6CF0.toInt())
            .setContentTitle(title)
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
        NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
    }

    companion object {
        const val ACTION_ALERT = "com.dailytodo.ALERT"
        const val ACTION_DONE = "com.dailytodo.MARK_DONE"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_TIME = "time"
    }
}
