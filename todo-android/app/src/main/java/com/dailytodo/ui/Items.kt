@file:OptIn(ExperimentalLayoutApi::class)

package com.dailytodo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailytodo.data.PlannedEvent
import com.dailytodo.data.Routine
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun RoutineItem(
    routine: Routine,
    done: Boolean,
    streak: Int,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** False for routines listed under "other days": they can be edited but not ticked today. */
    scheduledToday: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val toggle by rememberUpdatedState(onToggle)
    val delete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    toggle()
                    false // snap back; the card itself shows the new state
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    delete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.3f },
    )

    SwipeToDismissBox(
        state = state,
        modifier = modifier.clip(CardShape),
        enableDismissFromStartToEnd = scheduledToday,
        backgroundContent = {
            val direction = state.dismissDirection
            val colors = MaterialTheme.colorScheme
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeBackground(
                    color = if (done) colors.secondaryContainer else colors.tertiary,
                    contentColor = if (done) colors.onSecondaryContainer else Color.White,
                    icon = if (done) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.Check,
                    label = if (done) "Not done" else "Done!",
                    alignment = Alignment.CenterStart,
                )
                SwipeToDismissBoxValue.EndToStart -> DeleteBackground()
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        RoutineCard(routine, done, streak, scheduledToday, onToggle = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        }, onEdit = onEdit)
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    done: Boolean,
    streak: Int,
    scheduledToday: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dimmed = done || !scheduledToday
    val container by animateColorAsState(if (dimmed) colors.surfaceContainer else colors.surface, label = "container")
    val contentAlpha by animateFloatAsState(if (dimmed) 0.55f else 1f, label = "alpha")

    Surface(
        onClick = onEdit,
        shape = CardShape,
        color = container,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(if (done) colors.tertiaryContainer else colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(routine.emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier
                    .weight(1f)
                    .alpha(contentAlpha),
            ) {
                TitleWithNoteHint(routine.title, hasNote = routine.note.isNotBlank(), strike = done)
                Text(
                    when {
                        !scheduledToday -> scheduleLabel(routine.days)
                        streak >= 2 && routine.isEveryDay -> "🔥 $streak-day streak"
                        streak >= 2 -> "🔥 $streak in a row · ${scheduleLabel(routine.days)}"
                        !routine.isEveryDay -> scheduleLabel(routine.days)
                        done -> "Done for today"
                        else -> "Swipe right when done"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (scheduledToday) {
                Spacer(Modifier.width(8.dp))
                CheckCircle(checked = done, onClick = onToggle)
            }
        }
    }
}

/** The card shows only the name; a small icon hints that notes are inside. */
@Composable
private fun TitleWithNoteHint(title: String, hasNote: Boolean, strike: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (hasNote) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Rounded.Notes,
                contentDescription = "Has notes",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (checked) colors.tertiary else Color.Transparent, label = "fill")
    val scale by animateFloatAsState(
        if (checked) 1f else 0.4f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 400f),
        label = "check",
    )
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, if (checked) colors.tertiary else colors.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Rounded.Check, contentDescription = "Done",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .scale(scale),
            )
        }
    }
}

@Composable
private fun SwipeBackground(color: Color, contentColor: Color, icon: ImageVector, label: String, alignment: Alignment) {
    Box(
        Modifier
            .fillMaxSize()
            .background(color, CardShape)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Spacer(Modifier.width(8.dp))
            Text(label, color = contentColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DeleteBackground() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.errorContainer, CardShape)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Delete", color = colors.onErrorContainer, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = colors.onErrorContainer)
        }
    }
}

@Composable
fun EventItem(
    event: PlannedEvent,
    today: LocalDate,
    now: Long,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val delete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                delete()
                true
            } else false
        },
        positionalThreshold = { it * 0.3f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier.clip(CardShape),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) DeleteBackground()
        },
    ) {
        EventCard(event, today, now, onToggleDone, onEdit)
    }
}

@Composable
private fun EventCard(event: PlannedEvent, today: LocalDate, now: Long, onToggleDone: () -> Unit, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val next = event.nextTime(now)
    val faded = event.done || (next == null && event.times.isNotEmpty())
    val contentAlpha by animateFloatAsState(if (faded) 0.55f else 1f, label = "alpha")

    Surface(
        onClick = onEdit,
        shape = CardShape,
        color = if (faded) colors.surfaceContainer else colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            DateBadge(event.displayTime(now), faded, Modifier.alpha(contentAlpha))
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier
                    .weight(1f)
                    .alpha(contentAlpha),
            ) {
                TitleWithNoteHint(event.title, hasNote = event.note.isNotBlank(), strike = event.done)
                Spacer(Modifier.height(8.dp))
                when {
                    event.done -> MiniLabel("Completed ✓", colors.tertiaryContainer, colors.onTertiaryContainer)
                    event.times.isEmpty() -> MiniLabel("No alert set", colors.surfaceVariant, colors.onSurfaceVariant)
                    else -> FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (next != null) {
                            MiniLabel(countdown(next, now), colors.primary, colors.onPrimary)
                        }
                        event.times.sorted().forEach { t ->
                            val past = t <= now
                            MiniLabel(
                                formatAlert(t, today),
                                if (past) colors.surfaceVariant else colors.primaryContainer,
                                if (past) colors.onSurfaceVariant else colors.onPrimaryContainer,
                                strike = past,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CheckCircle(checked = event.done, onClick = onToggleDone)
                Spacer(Modifier.height(10.dp))
                Icon(
                    if (event.isActive(now)) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                    contentDescription = if (event.isActive(now)) "Alerts on" else "No upcoming alerts",
                    tint = if (event.isActive(now)) colors.primary else colors.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun DateBadge(time: Long?, faded: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val date = time?.toLocalDateTime()?.toLocalDate()
    Column(
        modifier
            .size(width = 54.dp, height = 60.dp)
            .background(if (faded) colors.surfaceVariant else colors.primaryContainer, RoundedCornerShape(16.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (date == null) {
            Text("🗓️", fontSize = 22.sp)
        } else {
            Text(
                date.format(DateTimeFormatter.ofPattern("MMM")).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (faded) colors.onSurfaceVariant else colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                date.dayOfMonth.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (faded) colors.onSurfaceVariant else colors.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun MiniLabel(text: String, container: Color, content: Color, strike: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        textDecoration = if (strike) TextDecoration.LineThrough else null,
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
