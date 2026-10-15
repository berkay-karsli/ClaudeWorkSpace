package com.dailytodo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailytodo.data.Space
import com.dailytodo.ui.theme.TodoTheme
import kotlinx.coroutines.launch

@Composable
fun TodoRoot(viewModel: TodoViewModel, tab: Tab, onTabChange: (Tab) -> Unit) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    RequestNotificationPermission()

    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }

    TodoTheme(tab) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { t ->
                        val remaining = t.space?.let { s -> data.routinesDueOn(s, today).count { !it.isDoneOn(today) } } ?: 0
                        val selected = t == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onTabChange(t) },
                            icon = {
                                BadgedBox(badge = { if (remaining > 0) Badge { Text("$remaining") } }) {
                                    Icon(t.icon(selected), contentDescription = null)
                                }
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally { it / 6 * dir } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 6 * dir } + fadeOut())
                },
                label = "tab",
            ) { shown ->
                TodoTheme(shown) {
                    val space = shown.space
                    if (space != null) {
                        SpaceScreen(
                            space = space,
                            data = data,
                            today = today,
                            now = now,
                            viewModel = viewModel,
                            bottomPadding = padding.calculateBottomPadding(),
                            offerUndo = ::offerUndo,
                        )
                    } else {
                        AlarmsScreen(
                            presets = data.presets,
                            viewModel = viewModel,
                            bottomPadding = padding.calculateBottomPadding(),
                            showMessage = ::showMessage,
                        )
                    }
                }
            }
        }
    }
}

val Space.label: String get() = if (this == Space.HOME) "Home" else "University"
val Space.emoji: String get() = if (this == Space.HOME) "🏡" else "🎓"

private val Tab.label: String get() = space?.label ?: "Alarms"

private fun Tab.icon(selected: Boolean) = when (this) {
    Tab.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
    Tab.UNIVERSITY -> if (selected) Icons.Rounded.School else Icons.Outlined.School
    Tab.ALARMS -> if (selected) Icons.Rounded.Alarm else Icons.Outlined.Alarm
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
