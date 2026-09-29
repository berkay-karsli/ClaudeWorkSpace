package com.voicemusic

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent

/** Carries out [Command]s against the YouTube Music app. Returns a short message describing the outcome. */
class YouTubeMusicController(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)

    fun execute(command: Command): String {
        if ((command is Command.OpenApp || command is Command.Play) && !isInstalled()) {
            openPlayStore()
            return "YouTube Music is not installed"
        }
        return when (command) {
            Command.OpenApp -> openApp()
            Command.Next -> transport("Next song", KeyEvent.KEYCODE_MEDIA_NEXT) { it.skipToNext() }
            Command.Previous -> transport("Previous song", KeyEvent.KEYCODE_MEDIA_PREVIOUS) { it.skipToPrevious() }
            Command.Pause -> transport("Paused", KeyEvent.KEYCODE_MEDIA_PAUSE) { it.pause() }
            Command.Resume -> transport("Playing", KeyEvent.KEYCODE_MEDIA_PLAY) { it.play() }
            Command.VolumeUp -> volume(AudioManager.ADJUST_RAISE, "Volume up")
            Command.VolumeDown -> volume(AudioManager.ADJUST_LOWER, "Volume down")
            is Command.Play -> play(command.query)
            is Command.Unknown -> "Didn't understand \"${command.text}\""
        }
    }

    fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** YouTube Music's live media session, if it has one and we have notification access. */
    private fun session(): MediaController? = try {
        sessionManager.getActiveSessions(MediaNotificationListener.component(context))
            .firstOrNull { it.packageName == PACKAGE }
    } catch (e: SecurityException) {
        null
    }

    private fun transport(
        label: String,
        fallbackKey: Int,
        action: (MediaController.TransportControls) -> Unit,
    ): String {
        val session = session()
        if (session != null) {
            action(session.transportControls)
        } else {
            // No session access: send a media button, which Android routes to the last music app.
            val now = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, fallbackKey, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, fallbackKey, 0))
        }
        return label
    }

    private fun volume(direction: Int, label: String): String {
        repeat(2) { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI) }
        return label
    }

    private fun play(query: String): String {
        // Same intent Google Assistant uses: YouTube Music searches and plays the top result. It
        // needs "Display over other apps" when this app is in the background.
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(PACKAGE)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Settings.canDrawOverlays(context)) return launch(intent, "Playing \"$query\"")

        // Without that permission, ask YouTube Music's running media session directly.
        val session = session()
        val actions = session?.playbackState?.actions ?: 0L
        if (session != null && actions and PlaybackState.ACTION_PLAY_FROM_SEARCH != 0L) {
            session.transportControls.playFromSearch(query, Bundle())
            return "Playing \"$query\""
        }
        return launch(intent, "Playing \"$query\"")
    }

    private fun openApp(): String {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return "YouTube Music is not installed"
        return launch(intent, "Opening YouTube Music")
    }

    private fun launch(intent: Intent, label: String): String = try {
        context.startActivity(intent)
        if (Settings.canDrawOverlays(context)) label
        else "$label (allow \"Display over other apps\" if nothing happens)"
    } catch (e: ActivityNotFoundException) {
        "YouTube Music couldn't handle that"
    }

    private fun openPlayStore() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No Play Store; the returned message is enough.
        }
    }

    companion object {
        const val PACKAGE = "com.google.android.apps.youtube.music"
    }
}
