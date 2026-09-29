package com.voicemusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import org.vosk.Model
import java.util.concurrent.Executors

/**
 * Foreground service that keeps the microphone open for the wake phrase, then listens for one
 * command and sends it to YouTube Music.
 */
class VoiceControlService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()
    private lateinit var musicController: YouTubeMusicController
    private lateinit var commandListener: CommandListener
    private lateinit var speaker: Speaker
    private lateinit var audioManager: AudioManager
    private var model: Model? = null
    private var detector: WakeWordDetector? = null
    private var tone: ToneGenerator? = null
    private var destroyed = false
    private var loading = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        musicController = YouTubeMusicController(this)
        commandListener = CommandListener(this)
        speaker = Speaker(this)
        audioManager = getSystemService(AudioManager::class.java)
        tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!startInForeground(getString(R.string.app_name))) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (model == null && !loading) loadModelAndListen()
        // Not sticky: Android doesn't allow restarting a microphone service from the background.
        return START_NOT_STICKY
    }

    private fun loadModelAndListen() {
        if (!ModelManager.isInstalled(this)) {
            updateStatus("Voice model is not downloaded yet")
            stopSelf()
            return
        }
        updateStatus("Loading voice model…")
        val modelPath = ModelManager.modelDir(this).absolutePath
        loading = true
        loader.execute {
            val loaded = runCatching { Model(modelPath) }
            mainHandler.post {
                loading = false
                loaded.onSuccess { m ->
                    if (destroyed) {
                        m.close()
                        return@onSuccess
                    }
                    model = m
                    detector = WakeWordDetector(m, Prefs.wakePhrase(this), ::onWakePhrase)
                    listenForWakePhrase()
                }.onFailure {
                    Log.e(TAG, "Could not load model", it)
                    updateStatus("Could not load voice model: ${it.message}")
                    stopSelf()
                }
            }
        }
    }

    private fun listenForWakePhrase() {
        if (destroyed) return
        detector?.start()
        updateStatus("Listening for \"${Prefs.wakePhrase(this)}\"")
    }

    private fun onWakePhrase() {
        detector?.pause()
        if (!commandListener.isAvailable) {
            updateStatus("No speech recognizer on this phone. Install or enable the Google app.")
            listenForWakePhrase()
            return
        }
        audioManager.requestAudioFocus(focusRequest)
        listenForCommand(retriesLeft = 1)
    }

    private fun listenForCommand(retriesLeft: Int) {
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        updateStatus("Listening… say a song name or a command")
        // Give the beep a moment so it isn't picked up as part of the command.
        mainHandler.postDelayed({
            if (destroyed) return@postDelayed
            commandListener.listen { results -> onCommandHeard(results, retriesLeft) }
        }, 250)
    }

    private fun onCommandHeard(results: List<String>, retriesLeft: Int) {
        if (destroyed) return
        if (results.isEmpty()) {
            updateStatus("Didn't catch that")
            if (retriesLeft > 0) {
                speaker.speak("Sorry, what should I play?") {
                    if (!destroyed) listenForCommand(retriesLeft - 1)
                }
            } else {
                audioManager.abandonAudioFocusRequest(focusRequest)
                speaker.speak("Sorry, I didn't catch that") { backToWakePhrase() }
            }
            return
        }

        val command = CommandParser.parseBest(results)
        val announcement = when (command) {
            is Command.Play -> "Playing ${command.query}"
            Command.OpenApp -> "Opening YouTube Music"
            else -> null
        }
        if (announcement == null) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            updateStatus(musicController.execute(command))
            backToWakePhrase()
            return
        }
        // Say what was heard before it starts, so a misheard title is obvious without looking.
        updateStatus(announcement)
        speaker.speak(announcement) {
            if (destroyed) return@speak
            audioManager.abandonAudioFocusRequest(focusRequest)
            updateStatus(musicController.execute(command))
            backToWakePhrase()
        }
    }

    private fun backToWakePhrase() {
        mainHandler.postDelayed(::listenForWakePhrase, 1_000)
    }

    override fun onDestroy() {
        destroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        commandListener.destroy()
        speaker.shutdown()
        audioManager.abandonAudioFocusRequest(focusRequest)
        detector?.shutdown()
        detector = null
        model?.close()
        model = null
        tone?.release()
        loader.shutdown()
        isRunning = false
        status = "Stopped"
        statusListener?.invoke(status)
        super.onDestroy()
    }

    private fun updateStatus(text: String) {
        isRunning = !destroyed
        status = text
        statusListener?.invoke(text)
        if (!destroyed) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun startInForeground(text: String): Boolean {
        val notification = buildNotification(text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: RuntimeException) {
            // Missing microphone permission, or started while the app wasn't in the foreground.
            Log.e(TAG, "Could not start in foreground", e)
            return false
        }
        isRunning = true
        return true
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VoiceControlService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stop).build())
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "VoiceControlService"
        private const val CHANNEL_ID = "voice_control"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.voicemusic.STOP"

        /** Read and written on the main thread only. */
        var isRunning = false
            private set
        var status = "Stopped"
            private set
        var statusListener: ((String) -> Unit)? = null

        fun start(context: Context) {
            context.startForegroundService(Intent(context, VoiceControlService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceControlService::class.java))
        }
    }
}
