@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dailytodo.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlarm
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dailytodo.data.PlannedEvent
import com.dailytodo.data.Routine
import com.dailytodo.data.Space
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

private val HomeEmojis = listOf("🛏️", "🧹", "🍽️", "💧", "🧺", "🌿", "🐾", "🛒", "🍳", "🚿", "🗑️", "💊", "🧘", "✨")
private val UniEmojis = listOf("📚", "✏️", "📝", "💻", "🧪", "🧠", "📖", "✉️", "🎧", "☕", "🗂️", "🎯", "🏃", "✨")

@Composable
fun RoutineEditor(
    space: Space,
    initial: Routine?,
    onDismiss: () -> Unit,
    onSave: (title: String, emoji: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val emojis = if (space == Space.HOME) HomeEmojis else UniEmojis
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var emoji by rememberSaveable { mutableStateOf(initial?.emoji ?: emojis.first()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                if (initial == null) "New daily routine" else "Edit routine",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "It resets every day, so you can tick it off again tomorrow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text("Pick an icon", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(emojis) { e ->
                    val selected = e == emoji
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                                CircleShape,
                            )
                            .border(
                                2.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                CircleShape,
                            )
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 22.sp) }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What do you do every day?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            SheetButtons(
                saveEnabled = title.isNotBlank(),
                onSave = { onSave(title, emoji) },
                onDelete = onDelete,
            )
        }
    }
}

@Composable
fun EventEditor(
    initial: PlannedEvent?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, note: String, times: List<Long>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    val times = remember { mutableStateListOf<Long>().apply { addAll(initial?.times?.sorted() ?: emptyList()) } }
    var pickingDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun addTime(dateTime: LocalDateTime) {
        val millis = dateTime.withSecond(0).withNano(0).toEpochMillis()
        when {
            millis <= System.currentTimeMillis() -> error = "That time has already passed."
            millis in times -> error = "That alert is already on the list."
            else -> {
                error = null
                val sorted = (times + millis).sorted()
                times.clear()
                times.addAll(sorted)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                if (initial == null) "New planned event" else "Edit event",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Add one or more times and you'll get a notification at each.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Event") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.NotificationsActive, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text("  Alert times", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            if (times.isEmpty()) {
                Text(
                    "No alerts yet — add a time below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    times.toList().forEach { t ->
                        InputChip(
                            selected = true,
                            onClick = { times.remove(t) },
                            label = { Text(formatAlert(t, today)) },
                            trailingIcon = {
                                Icon(Icons.Rounded.Close, "Remove", Modifier.size(InputChipDefaults.IconSize))
                            },
                        )
                    }
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AddAlarm, null, Modifier.size(18.dp))
                Text("  Add a date & time")
            }

            Spacer(Modifier.height(12.dp))
            Text("Quick add", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val now = LocalDateTime.now()
                QuickChip("In 1 hour") { addTime(now.plusHours(1)) }
                if (now.hour < 20) QuickChip("Tonight 20:00") { addTime(now.toLocalDate().atTime(20, 0)) }
                QuickChip("Tomorrow 09:00") { addTime(now.toLocalDate().plusDays(1).atTime(9, 0)) }
                QuickChip("Next week") { addTime(now.toLocalDate().plusWeeks(1).atTime(9, 0)) }
            }
            times.maxOrNull()?.let { latest ->
                val base = latest.toLocalDateTime()
                Spacer(Modifier.height(4.dp))
                Text(
                    "Also remind me before ${formatAlert(latest, today)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickChip("10 min") { addTime(base.minusMinutes(10)) }
                    QuickChip("1 hour") { addTime(base.minusHours(1)) }
                    QuickChip("1 day") { addTime(base.minusDays(1)) }
                }
            }

            Spacer(Modifier.height(20.dp))
            SheetButtons(
                saveEnabled = title.isNotBlank(),
                onSave = { onSave(title, note, times.toList()) },
                onDelete = onDelete,
            )
        }
    }

    if (pickingDate) {
        DateDialog(
            today = today,
            onDismiss = { pickingDate = false },
            onPicked = {
                pickingDate = false
                pickedDate = it
            },
        )
    }
    pickedDate?.let { date ->
        TimeDialog(
            onDismiss = { pickedDate = null },
            onPicked = { time ->
                pickedDate = null
                addTime(date.atTime(time))
            },
        )
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    SuggestionChip(onClick = onClick, label = { Text(label) })
}

@Composable
private fun SheetButtons(saveEnabled: Boolean, onSave: () -> Unit, onDelete: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onDelete != null) {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onSave, enabled = saveEnabled, contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)) {
            Text("Save")
        }
    }
}

@Composable
private fun DateDialog(today: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val todayUtc = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = todayUtc,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
            override fun isSelectableYear(year: Int) = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) { Text("Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun TimeDialog(onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val start = LocalTime.now().plusHours(1)
    val state = rememberTimePickerState(
        initialHour = start.hour,
        initialMinute = 0,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "At what time?",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                )
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text("Add alert") }
                }
            }
        }
    }
}
