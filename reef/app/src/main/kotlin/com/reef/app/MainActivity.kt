package com.reef.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.reef.app.ui.ReefApp
import com.reef.app.ui.ReefTheme
import com.reef.app.ui.SaveStore
import java.io.File

/** Keeps the game in progress in one file in the app's private storage. */
private class FileSaveStore(private val file: File) : SaveStore {
    override fun load(): String? = if (file.exists()) file.readText() else null

    override fun save(json: String) {
        // Write then rename, so a crash mid-save never leaves a broken file.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        tmp.renameTo(file)
    }

    override fun clear() {
        file.delete()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The board needs every pixel: hide the status and navigation bars until swiped in.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val store = FileSaveStore(File(filesDir, "reef-save.json"))
        setContent {
            ReefTheme {
                ReefApp(store) { enabled, onBack -> BackHandler(enabled, onBack) }
            }
        }
    }
}
