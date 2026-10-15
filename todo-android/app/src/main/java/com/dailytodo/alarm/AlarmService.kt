package com.dailytodo.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dailytodo.R
import com.dailytodo.data.Space
import com.dailytodo.todoApp
import java.time.LocalDate

/**
 * Rings a to-do alarm: plays its sound on the alarm stream (getting louder over a few seconds),
 * vibrates, and shows a full-screen alarm screen until it is stopped, snoozed or times out.
 */
class AlarmService : Service() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopAlarm() }
    private var volume = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopAlarm()
            ACTION_SNOOZE -> {
                AlarmState.ringing.value?.let { todoApp.scheduler.snooze(it, SNOOZE_MINUTES) }
                stopAlarm()
            }
            ACTION_DONE -> {
                AlarmState.ringing.value?.let(::markDone)
                stopAlarm()
            }
            else -> {
                val alarm = RingingAlarm.from(intent)
                if (alarm == null) stopAlarm() else ring(alarm)
            }
        }
        return START_NOT_STICKY
    }

    private fun ring(alarm: RingingAlarm) {
        stopSound()
        AlarmState.ringing.value = alarm
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(alarm), type)

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DailyTodo:alarm")
            .apply { acquire(RING_MILLIS + 10_000) }
        playSound(alarm.sound)
        vibrate()

        // Without notifications there is no Stop button, so don't ring for long.
        val canShowStop = NotificationManagerCompat.from(this).areNotificationsEnabled()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, if (canShowStop) RING_MILLIS else 60_000L)
    }

    private fun playSound(sound: String) {
        val uri = Sounds.uriOf(sound) ?: return // silent: vibrate only
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val candidates = listOf(uri, Settings.System.DEFAULT_ALARM_ALERT_URI, Settings.System.DEFAULT_RINGTONE_URI)
        val tone = candidates.firstNotNullOfOrNull { RingtoneManager.getRingtone(this, it) } ?: return
        try {
            tone.audioAttributes = attributes
            if (Build.VERSION.SDK_INT >= 28) {
                tone.isLooping = true
                volume = 0.15f
                tone.volume = volume
                handler.post(rampUp)
            }
            tone.play()
            ringtone = tone
            if (Build.VERSION.SDK_INT < 28) handler.postDelayed(keepPlaying, 1_000)
        } catch (e: Exception) {
            Log.e("AlarmService", "Could not play alarm sound", e)
        }
    }

    /** Fades the sound in over ~10 seconds. */
    private val rampUp: Runnable = object : Runnable {
        override fun run() {
            val tone = ringtone ?: return
            volume = (volume + 0.085f).coerceAtMost(1f)
            if (Build.VERSION.SDK_INT >= 28) tone.volume = volume
            if (volume < 1f) handler.postDelayed(this, 1_000)
        }
    }

    /** Older Android can't loop a Ringtone, so restart it when it ends. */
    private val keepPlaying: Runnable = object : Runnable {
        override fun run() {
            val tone = ringtone ?: return
            if (!tone.isPlaying) tone.play()
            handler.postDelayed(this, 1_000)
        }
    }

    private fun vibrate() {
        val vib = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 700), 0), attributes)
        vibrator = vib
    }

    private fun stopSound() {
        handler.removeCallbacks(rampUp)
        handler.removeCallbacks(keepPlaying)
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopAlarm() {
        handler.removeCallbacks(timeout)
        stopSound()
        AlarmState.ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSound()
        AlarmState.ringing.value = null
        super.onDestroy()
    }

    private fun markDone(alarm: RingingAlarm) {
        val app = todoApp
        when (alarm.kind) {
            RingingAlarm.KIND_ROUTINE -> app.repository.markRoutineDone(alarm.id, LocalDate.now())
            else -> {
                app.repository.markEventDone(alarm.id)
                app.repository.event(alarm.id)?.let(app.scheduler::cancel)
            }
        }
    }

    private fun buildNotification(alarm: RingingAlarm): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, ReminderScheduler.RINGING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(if (alarm.space == Space.HOME) 0xFFE8735A.toInt() else 0xFF5B6CF0.toInt())
            .setContentTitle("${alarm.emoji} ${alarm.title}")
            .setContentText(if (alarm.space == Space.HOME) "🏡 Home" else "🎓 University")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Snooze $SNOOZE_MINUTES min", servicePending(ACTION_SNOOZE))
            .addAction(0, "Stop", servicePending(ACTION_STOP))
            .build()
    }

    private fun servicePending(action: String) = PendingIntent.getService(
        this, action.hashCode(), Intent(this, AlarmService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val SNOOZE_MINUTES = 10
        private const val RING_MILLIS = 5 * 60_000L
        private const val NOTIFICATION_ID = 7001
        private const val ACTION_STOP = "com.dailytodo.alarm.STOP"
        private const val ACTION_SNOOZE = "com.dailytodo.alarm.SNOOZE"
        private const val ACTION_DONE = "com.dailytodo.alarm.DONE"

        /** Starts ringing; returns false if Android didn't allow it (the caller then notifies instead). */
        fun start(context: Context, alarm: RingingAlarm): Boolean = try {
            ContextCompat.startForegroundService(context, alarm.writeTo(Intent(context, AlarmService::class.java)))
            true
        } catch (e: Exception) {
            Log.e("AlarmService", "Could not start ringing", e)
            false
        }

        fun stop(context: Context) = send(context, ACTION_STOP)
        fun snooze(context: Context) = send(context, ACTION_SNOOZE)
        fun markDone(context: Context) = send(context, ACTION_DONE)

        private fun send(context: Context, action: String) {
            runCatching { context.startService(Intent(context, AlarmService::class.java).setAction(action)) }
        }
    }
}
