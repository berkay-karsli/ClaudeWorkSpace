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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
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
fun TodoRoot(viewModel: TodoViewModel, space: Space, onSpaceChange: (Space) -> Unit) {
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

    TodoTheme(space) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Space.entries.forEach { s ->
                        val remaining = data.routinesDueOn(s, today).count { !it.isDoneOn(today) }
                        val selected = s == space
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onSpaceChange(s) },
                            icon = {
                                BadgedBox(badge = { if (remaining > 0) Badge { Text("$remaining") } }) {
                                    Icon(s.icon(selected), contentDescription = null)
                                }
                            },
                            label = { Text(s.label) },
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
                targetState = space,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally { it / 6 * dir } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 6 * dir } + fadeOut())
                },
                label = "space",
            ) { shown ->
                TodoTheme(shown) {
                    SpaceScreen(
                        space = shown,
                        data = data,
                        today = today,
                        now = now,
                        viewModel = viewModel,
                        bottomPadding = padding.calculateBottomPadding(),
                        offerUndo = ::offerUndo,
                    )
                }
            }
        }
    }
}

val Space.label: String get() = if (this == Space.HOME) "Home" else "University"
val Space.emoji: String get() = if (this == Space.HOME) "🏡" else "🎓"

private fun Space.icon(selected: Boolean) = when (this) {
    Space.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
    Space.UNIVERSITY -> if (selected) Icons.Rounded.School else Icons.Outlined.School
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
