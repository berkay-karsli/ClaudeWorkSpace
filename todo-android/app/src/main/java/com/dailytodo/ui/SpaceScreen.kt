package com.dailytodo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dailytodo.data.PlannedEvent
import com.dailytodo.data.Routine
import com.dailytodo.data.Space
import com.dailytodo.data.TodoData
import com.dailytodo.ui.theme.SectionTitle
import java.time.LocalDate
import java.time.LocalTime

/** What the editor sheet is currently showing, if anything. */
private sealed interface Editing {
    data class RoutineSheet(val routine: Routine?) : Editing
    data class EventSheet(val event: PlannedEvent?) : Editing
}

@Composable
fun SpaceScreen(
    space: Space,
    data: TodoData,
    today: LocalDate,
    now: Long,
    viewModel: TodoViewModel,
    bottomPadding: Dp,
    offerUndo: (String, () -> Unit) -> Unit,
) {
    val routines = data.routinesDueOn(space, today)
    val otherDays = data.routinesIn(space).filterNot { it.isScheduledOn(today) }
    val events = data.eventsIn(space, now)
    var editing by remember { mutableStateOf<Editing?>(null) }
    var showOtherDays by rememberSaveable { mutableStateOf(false) }

    fun deleteRoutine(routine: Routine) {
        val index = viewModel.routineIndex(routine)
        viewModel.deleteRoutine(routine)
        offerUndo("Routine deleted") { viewModel.restoreRoutine(routine, index) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Header(space, routines, events, today, now)
        }

        item(key = "routines-title") {
            SectionHeader(
                title = "Daily routines",
                subtitle = "Today's list renews every morning · swipe right when done",
                onAdd = { editing = Editing.RoutineSheet(null) },
            )
        }
        if (routines.isEmpty()) {
            item(key = "routines-empty") {
                if (otherDays.isEmpty()) {
                    EmptyCard("🌱", "No routines yet", "Add the little things you do every day.")
                } else {
                    EmptyCard("☕", "Nothing on today's list", "Enjoy the free day — your other routines are below.")
                }
            }
        }
        items(routines, key = { "r${it.id}" }) { routine ->
            RoutineItem(
                routine = routine,
                done = routine.isDoneOn(today),
                streak = routine.streak(today),
                onToggle = { viewModel.toggleRoutine(routine.id) },
                onEdit = { editing = Editing.RoutineSheet(routine) },
                onDelete = { deleteRoutine(routine) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .animateItem(),
            )
        }
        if (otherDays.isNotEmpty()) {
            item(key = "other-days-toggle") {
                OtherDaysToggle(count = otherDays.size, expanded = showOtherDays) { showOtherDays = !showOtherDays }
            }
        }
        if (showOtherDays) {
            items(otherDays, key = { "o${it.id}" }) { routine ->
                RoutineItem(
                    routine = routine,
                    done = false,
                    streak = 0,
                    scheduledToday = false,
                    onToggle = {},
                    onEdit = { editing = Editing.RoutineSheet(routine) },
                    onDelete = { deleteRoutine(routine) },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }
        }

        item(key = "events-title") {
            Column(Modifier.padding(top = 8.dp)) {
                SectionHeader(
                    title = "Planned events",
                    subtitle = "Pick one or more times to be alerted",
                    onAdd = { editing = Editing.EventSheet(null) },
                )
            }
        }
        if (events.isEmpty()) {
            item(key = "events-empty") {
                EmptyCard("🗓️", "Nothing planned", "Add an event and choose when to be reminded.")
            }
        }
        items(events, key = { "e${it.id}" }) { event ->
            EventItem(
                event = event,
                today = today,
                now = now,
                onToggleDone = { viewModel.toggleEventDone(event) },
                onEdit = { editing = Editing.EventSheet(event) },
                onDelete = {
                    viewModel.deleteEvent(event)
                    offerUndo("Event deleted") { viewModel.restoreEvent(event) }
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .animateItem(),
            )
        }
    }

    when (val e = editing) {
        is Editing.RoutineSheet -> RoutineEditor(
            space = space,
            initial = e.routine,
            onDismiss = { editing = null },
            onSave = { title, emoji, note, days ->
                viewModel.saveRoutine(e.routine, space, title, emoji, note, days)
                editing = null
            },
            onDelete = e.routine?.let { r ->
                {
                    editing = null
                    deleteRoutine(r)
                }
            },
        )
        is Editing.EventSheet -> EventEditor(
            initial = e.event,
            today = today,
            onDismiss = { editing = null },
            onSave = { title, note, times ->
                viewModel.saveEvent(e.event, space, title, note, times)
                editing = null
            },
            onDelete = e.event?.let { ev ->
                {
                    editing = null
                    viewModel.deleteEvent(ev)
                    offerUndo("Event deleted") { viewModel.restoreEvent(ev) }
                }
            },
        )
        null -> Unit
    }
}

@Composable
private fun Header(space: Space, routines: List<Routine>, events: List<PlannedEvent>, today: LocalDate, now: Long) {
    val colors = MaterialTheme.colorScheme
    val done = routines.count { it.isDoneOn(today) }
    val total = routines.size
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else done.toFloat() / total,
        animationSpec = tween(600),
        label = "progress",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(colors.primaryContainer, colors.secondaryContainer.copy(alpha = 0.6f), colors.background)),
            )
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        today.format(headerDateFormat).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${space.emoji}  ${space.label}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        greeting(LocalTime.now().hour),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(84.dp),
                        color = if (total > 0 && done == total) colors.tertiary else colors.primary,
                        trackColor = colors.surface.copy(alpha = 0.7f),
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$done/$total", style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
                        Text("today", style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            val next = events.filter { it.isActive(now) }.minByOrNull { it.nextTime(now)!! }
            HeaderNote(
                when {
                    next != null -> "Next up: ${next.title} · ${formatAlert(next.nextTime(now)!!, today)}"
                    total > 0 && done == total -> "Every routine is done today — lovely work! 🎉"
                    total > 0 -> "${total - done} routine${if (total - done == 1) "" else "s"} left for today. You've got this 💪"
                    else -> "A calm, fresh start. Add something below ✨"
                },
                showBell = next != null,
            )
        }
    }
}

@Composable
private fun HeaderNote(text: String, showBell: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBell) {
                Icon(
                    Icons.Rounded.NotificationsActive, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SectionTitle, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = "Add to $title")
        }
    }
}

@Composable
private fun OtherDaysToggle(count: Int, expanded: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (expanded) "Hide routines for other days"
                else "$count more routine${if (count == 1) "" else "s"} on other days",
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
        }
    }
}

@Composable
private fun EmptyCard(emoji: String, title: String, text: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
