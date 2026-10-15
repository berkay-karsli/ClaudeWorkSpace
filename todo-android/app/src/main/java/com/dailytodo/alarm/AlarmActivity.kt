package com.dailytodo.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailytodo.data.Space
import com.dailytodo.ui.formatMinuteOfDay
import com.dailytodo.ui.headerDateFormat
import com.dailytodo.ui.tab
import com.dailytodo.ui.theme.TodoTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/** Full-screen "ringing" screen, shown over the lock screen while a to-do alarm goes off. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        setContent {
            val ringing by AlarmState.ringing.collectAsStateWithLifecycle()
            val alarm = ringing
            // Close once the alarm is stopped, snoozed or times out (here or from the notification).
            LaunchedEffect(alarm) { if (alarm == null) finish() }
            if (alarm != null) {
                TodoTheme(alarm.space.tab) {
                    RingingScreen(
                        alarm = alarm,
                        onStop = { AlarmService.stop(this) },
                        onSnooze = { AlarmService.snooze(this) },
                        onDone = { AlarmService.markDone(this) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RingingScreen(alarm: RingingAlarm, onStop: () -> Unit, onSnooze: () -> Unit, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.background)))
            .safeDrawingPadding()
            .padding(28.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Text(
                    formatMinuteOfDay(now.hour * 60 + now.minute),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                )
                Text(
                    LocalDate.now().format(headerDateFormat),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(132.dp)
                        .scale(scale)
                        .background(colors.primary.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .background(colors.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(alarm.emoji, fontSize = 44.sp) }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    alarm.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = colors.onBackground,
                )
                Text(
                    if (alarm.space == Space.HOME) "🏡 Home" else "🎓 University",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) { Text("Stop", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text("Snooze ${AlarmService.SNOOZE_MINUTES} min", fontSize = 17.sp) }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDone) { Text("Stop & mark as done ✓") }
            }
        }
    }
}
