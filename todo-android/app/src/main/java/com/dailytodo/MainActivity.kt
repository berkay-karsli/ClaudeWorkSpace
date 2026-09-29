package com.dailytodo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dailytodo.data.Space
import com.dailytodo.ui.TodoRoot
import com.dailytodo.ui.TodoViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: TodoViewModel by viewModels()
    private var requestedSpace by mutableStateOf<Space?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedSpace = spaceFrom(intent)
        setContent {
            var space by rememberSaveable { mutableStateOf(requestedSpace ?: Space.HOME) }
            // Opening the app from an event notification jumps to that event's space.
            LaunchedEffect(requestedSpace) {
                requestedSpace?.let {
                    space = it
                    requestedSpace = null
                }
            }
            TodoRoot(viewModel, space, onSpaceChange = { space = it })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedSpace = spaceFrom(intent)
    }

    override fun onResume() {
        super.onResume()
        // A new day may have started while the app was in the background.
        viewModel.refreshClock()
    }

    private fun spaceFrom(intent: Intent?): Space? =
        intent?.getStringExtra(EXTRA_SPACE)?.let { runCatching { Space.valueOf(it) }.getOrNull() }

    companion object {
        const val EXTRA_SPACE = "space"
    }
}
