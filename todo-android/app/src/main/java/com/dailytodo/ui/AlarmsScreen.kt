@file:OptIn(ExperimentalLayoutApi::class)

package com.dailytodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailytodo.alarm.ClockApp
import com.dailytodo.data.AlarmPreset
import com.dailytodo.ui.theme.SectionTitle
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * The alarm ladder: saved lists of steps that are lined up with a deadline and sent to the
 * phone's clock app as real alarms.
 */
@Composable
fun AlarmsScreen(
    presets: List<AlarmPreset>,
    viewModel: TodoViewModel,
    bottomPadding: Dp,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf(presets.firstOrNull()?.id ?: 0L) }
    val preset = presets.firstOrNull { it.id == selectedId } ?: presets.firstOrNull()
    var pickingDeadline by remember { mutableStateOf(false) }
    var addingStep by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Which alarm is being sent to the clock app right now (1-based), or null when idle.
    var sending by remember { mutableStateOf<Int?>(null) }

    fun update(change: (AlarmPreset) -> AlarmPreset) {
        preset?.let { viewModel.updatePreset(it.id, change) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { AlarmsHeader() }

        item(key = "presets") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(presets, key = { it.id }) { p ->
                    FilterChip(
                        selected = p.id == preset?.id,
                        onClick = { selectedId = p.id },
                        label = { Text(p.name.ifBlank { "Untitled" }) },
                    )
                }
                item(key = "new") {
                    AssistChip(
                        onClick = { selectedId = viewModel.addPreset() },
                        label = { Text("New ladder") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }

        if (preset == null) {
            item(key = "empty") {
                Text(
                    "No ladders yet. Tap “New ladder” to make one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            return@LazyColumn
        }

        item(key = "deadline-${preset.id}") {
            DeadlineCard(preset.deadline) { pickingDeadline = true }
        }

        item(key = "steps-${preset.id}") {
            StepsCard(
                preset = preset,
                onRemove = { step -> update { it.copy(steps = it.steps - step) } },
                onAdd = { addingStep = true },
                onAddAfterLast = { gap ->
                    update {
                        val next = it.sortedSteps.last() + gap
                        if (next < AlarmPreset.MINUTES_PER_DAY) it.copy(steps = it.steps + next) else it
                    }
                },
            )
        }

        item(key = "settings-${preset.id}") {
            LadderCard {
                var name by remember(preset.id) { mutableStateOf(preset.name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        update { p -> p.copy(name = it) }
                    },
                    label = { Text("Ladder name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                SoundRow(preset.sound, { s -> update { it.copy(sound = s) } }, label = "Sound in the clock app")
            }
        }

        item(key = "apply-${preset.id}") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val count = preset.sortedSteps.size
                Button(
                    onClick = {
                        scope.launch {
                            val result = ClockApp.setAlarms(context, preset) { sending = it }
                            sending = null
                            showMessage(
                                when (result) {
                                    is ClockApp.Result.Done ->
                                        "✓ Sent ${result.count} alarm${if (result.count == 1) "" else "s"} to your clock app"
                                    ClockApp.Result.NoClockApp -> "No clock app on this phone accepts new alarms"
                                },
                            )
                        }
                    },
                    enabled = sending == null,
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val progress = sending
                    if (progress != null) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("   Setting alarm $progress of $count…")
                    } else {
                        Icon(Icons.Rounded.Alarm, null, Modifier.size(20.dp))
                        Text("   Apply · set $count alarm${if (count == 1) "" else "s"} in my clock app")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your clock app keeps these alarms. Turn them off or delete them there when you're done.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete this ladder", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (preset != null && pickingDeadline) {
        TimeDialog(
            title = "Deadline",
            confirmLabel = "Set",
            initial = LocalTime.of(preset.deadline / 60, preset.deadline % 60),
            onDismiss = { pickingDeadline = false },
            onPicked = { time ->
                pickingDeadline = false
                update { it.copy(deadline = time.hour * 60 + time.minute) }
            },
        )
    }
    if (preset != null && addingStep) {
        val suggestion = (preset.sortedSteps.last() + 10).coerceAtMost(AlarmPreset.MINUTES_PER_DAY - 1)
        TimeDialog(
            title = "Add a step (hh:mm on your timeline)",
            confirmLabel = "Add",
            initial = LocalTime.of(suggestion / 60, suggestion % 60),
            force24Hour = true,
            onDismiss = { addingStep = false },
            onPicked = { time ->
                addingStep = false
                update { it.copy(steps = (it.steps + (time.hour * 60 + time.minute)).distinct()) }
            },
        )
    }
    if (preset != null && confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${preset.name.ifBlank { "Untitled" }}”?") },
            text = { Text("Alarms already sent to your clock app stay there.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deletePreset(preset)
                    selectedId = presets.firstOrNull { it.id != preset.id }?.id ?: 0L
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AlarmsHeader() {
    val colors = MaterialTheme.colorScheme
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
            Text(
                "ALARM LADDER",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text("⏰  Alarms", style = MaterialTheme.typography.headlineLarge, color = colors.onPrimaryContainer)
            Spacer(Modifier.height(2.dp))
            Text(
                "Line up a set of alarms with a deadline, then send them all to your clock app in one tap.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun LadderCard(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun DeadlineCard(deadline: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = colors.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Deadline", style = MaterialTheme.typography.labelLarge, color = colors.onPrimary.copy(alpha = 0.8f))
                Text(
                    formatMinuteOfDay(deadline),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimary,
                )
                Text(
                    "The last alarm rings at this time",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimary.copy(alpha = 0.8f),
                )
            }
            Icon(Icons.Rounded.Edit, contentDescription = "Change deadline", tint = colors.onPrimary)
        }
    }
}

@Composable
private fun StepsCard(
    preset: AlarmPreset,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onAddAfterLast: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val steps = preset.sortedSteps
    val times = preset.alarmTimes()
    LadderCard {
        Text("Steps", style = SectionTitle)
        Text(
            "Times on a timeline. The latest step lines up with the deadline; the others keep their distance from it.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        steps.zip(times).forEach { (step, time) ->
            val before = preset.minutesBefore(step)
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatStep(step),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .background(colors.surfaceContainer, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .widthIn(min = 40.dp),
                )
                Text("  →  ", color = colors.outline)
                Column(Modifier.weight(1f)) {
                    Text(formatMinuteOfDay(time), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (before == 0) "deadline" else "${ClockApp.durationLabel(before)} before",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (before == 0) colors.primary else colors.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onRemove(step) }, enabled = steps.size > 1) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove step ${formatStep(step)}")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Text("  Add a step")
        }
        Spacer(Modifier.height(6.dp))
        Text("Add after the last step", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30, 60).forEach { gap ->
                SuggestionChip(onClick = { onAddAfterLast(gap) }, label = { Text("+${ClockApp.durationLabel(gap)}") })
            }
        }
    }
}
