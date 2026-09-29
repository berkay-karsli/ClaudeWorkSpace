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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import java.util.concurrent.Executors

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
     * YouTube Music ignores "play from search" requests from apps other than Google's and only
     * fills in its search box, so this looks up the top song itself and opens that song's link,
     * which YouTube Music starts playing right away.
     */
    fun play(query: String, onResult: (String) -> Unit) {
        if (!isInstalled()) {
            openPlayStore()
            onResult("YouTube Music is not installed")
            return
        }
        lookups.execute {
            val videoId = runCatching { YouTubeMusicSearch.findSong(query) }.getOrNull()
            handler.post {
                if (videoId == null) {
                    onResult(openSearch(query, "Couldn't look up the song, check the internet connection"))
                    return@post
                }
                val before = session()?.let(::trackKey)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/watch?v=$videoId"))
                    .setPackage(PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    onResult("YouTube Music couldn't open the song")
                    return@post
                }
                if (!Settings.canDrawOverlays(context)) {
                    onResult("Allow display over other apps so songs can start while this app is in the background")
                    return@post
                }
                announceWhenStarted(before, query, onResult)
            }
        }
    }

    /** Waits for the new song to start so its real title and artist can be read out. */
    private fun announceWhenStarted(before: String?, query: String, onResult: (String) -> Unit) {
        val deadline = SystemClock.uptimeMillis() + START_WAIT_MS
        val poll = object : Runnable {
            override fun run() {
                val session = session()
                val metadata = session?.metadata
                val state = session?.playbackState?.state
                val started = session != null && trackKey(session) != before &&
                    (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING)
                when {
                    started && metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null -> {
                        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        onResult("Playing $title" + (artist?.let { " by $it" } ?: ""))
                    }
                    // Without notification access (or if it's slow) we can't confirm; it's been opened.
                    SystemClock.uptimeMillis() >= deadline -> onResult("Playing \"$query\"")
                    else -> handler.postDelayed(this, 300)
                }
            }
        }
        handler.postDelayed(poll, 500)
    }

    private fun trackKey(session: MediaController): String? = session.metadata?.let {
        it.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: it.getString(MediaMetadata.METADATA_KEY_TITLE)
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
        private const val START_WAIT_MS = 6_000L
        private val lookups = Executors.newSingleThreadExecutor()
    }
}
