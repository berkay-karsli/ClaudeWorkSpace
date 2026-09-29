package com.voicemusic

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent

/** Carries out [Command]s against the YouTube Music app. Returns a short message describing the outcome. */
class YouTubeMusicController(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    fun execute(command: Command): String {
        if (command is Command.OpenApp && !isInstalled()) {
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
            is Command.Play -> {
                play(command.query) {}
                "Looking for \"${command.query}\""
            }
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
            pressMediaKey(fallbackKey)
        }
        return label
    }

    /** Sends a media button, which Android routes to the last music app (usually YouTube Music). */
    private fun pressMediaKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun volume(direction: Int, label: String): String {
        repeat(2) { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI) }
        return label
    }

    /**
     * Plays [query] and reports the outcome through [onResult] (on the main thread).
     *
     * YouTube Music's "play from search" intent only fills in its search box, so songs are started
     * through its media session instead, the way Android Auto does. If YouTube Music isn't running,
     * a "play" media button wakes its player first. The search intent is only a last resort.
     */
    fun play(query: String, onResult: (String) -> Unit) {
        if (!isInstalled()) {
            openPlayStore()
            onResult("YouTube Music is not installed")
            return
        }
        if (!MediaNotificationListener.isEnabled(context)) {
            onResult(openSearch(query, "Allow notification access so songs start by themselves"))
            return
        }
        val session = session()
        if (session != null) {
            playFromSession(session, query, onResult)
            return
        }
        // Wake YouTube Music's player (it's normally the app that last played music), then wait for its session.
        pressMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        waitForSession(SESSION_WAIT_MS) { woken ->
            if (woken != null) {
                // Give the player a moment to finish starting before asking it for the song.
                handler.postDelayed({ playFromSession(woken, query, onResult) }, 1_000)
            } else {
                onResult(openSearch(query, "Couldn't start YouTube Music's player"))
            }
        }
    }

    private fun playFromSession(session: MediaController, query: String, onResult: (String) -> Unit) {
        val before = trackKey(session)
        session.transportControls.playFromSearch(query, Bundle())
        // Check that something new actually started; otherwise fall back to the search screen.
        handler.postDelayed({
            val current = session() ?: session
            val state = current.playbackState?.state
            val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING ||
                state == PlaybackState.STATE_CONNECTING
            val changed = trackKey(current) != before
            if (playing && changed) {
                val title = current.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                val artist = current.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                onResult(if (title != null) "Playing $title" + (artist?.let { " by $it" } ?: "") else "Playing \"$query\"")
            } else {
                onResult(openSearch(query, "YouTube Music didn't start it"))
            }
        }, VERIFY_DELAY_MS)
    }

    private fun trackKey(session: MediaController): String? = session.metadata?.let {
        it.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: it.getString(MediaMetadata.METADATA_KEY_TITLE)
    }

    private fun waitForSession(timeoutMs: Long, onDone: (MediaController?) -> Unit) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        val poll = object : Runnable {
            override fun run() {
                val session = session()
                when {
                    session != null -> onDone(session)
                    SystemClock.uptimeMillis() >= deadline -> onDone(null)
                    else -> handler.postDelayed(this, 250)
                }
            }
        }
        handler.post(poll)
    }

    /** Opens YouTube Music's search for [query]; the user has to tap the result. */
    private fun openSearch(query: String, reason: String): String {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(PACKAGE)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launch(intent, "")
        return "$reason. Opened search for \"$query\""
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
        private const val SESSION_WAIT_MS = 5_000L
        private const val VERIFY_DELAY_MS = 3_500L
    }
}
