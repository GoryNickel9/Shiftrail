package it.turni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.turni.app.R

private val reminderChoices = listOf<Int?>(null, 0, 5, 10, 15, 30, 60, 120, 1440)
private val eventColors =
    listOf(
        0xff1a73e8.toInt() to R.string.color_blue,
        0xff0b8043.toInt() to R.string.color_green,
        0xff8e24aa.toInt() to R.string.color_purple,
        0xffe67c00.toInt() to R.string.color_orange,
        0xffd50000.toInt() to R.string.color_red,
        0xff616161.toInt() to R.string.color_gray,
    )

@Composable
private fun reminderLabel(minutes: Int?): String =
    when (minutes) {
        null -> stringResource(R.string.reminder_none)
        0 -> stringResource(R.string.reminder_at_start)
        60 -> stringResource(R.string.reminder_one_hour)
        120 -> stringResource(R.string.reminder_two_hours)
        1440 -> stringResource(R.string.reminder_one_day)
        else -> pluralStringResource(R.plurals.reminder_minutes, requireNotNull(minutes), minutes)
    }

@Composable
private fun colorLabel(color: Int?): String =
    when {
        color == null -> stringResource(R.string.color_calendar)
        else ->
            eventColors.firstOrNull { it.first == color }?.second?.let { stringResource(it) }
                ?: "#%06X".format(color and 0xffffff)
    }

@Composable
fun SettingsScreen(state: AppState, vm: AppViewModel, onCalendar: () -> Unit, onSaved: () -> Unit) {
    val focus = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf(state.profile?.name.orEmpty()) }
    var reminder by rememberSaveable { mutableStateOf(state.settings.reminderMinutes) }
    var workColor by rememberSaveable { mutableStateOf(state.settings.eventColor) }
    var restColor by rememberSaveable { mutableStateOf(state.settings.restColor) }
    var saveRestDays by rememberSaveable { mutableStateOf(state.settings.saveRestDays) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var choosingReminder by rememberSaveable { mutableStateOf(false) }
    var choosingColor by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            item {
                OutlinedTextField(
                    name,
                    {
                        name = it
                        invalid = false
                    },
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = invalid,
                )
            }
            item {
                Text(
                    stringResource(R.string.calendar_preferences),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.event_titles_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                val calendar = state.calendars.firstOrNull { it.id == state.settings.calendarId }
                SettingChoice(
                    stringResource(R.string.choose_calendar),
                    calendar?.let { "${it.name}\n${it.account}" }
                        ?: stringResource(
                            if (state.settings.calendarId == null) R.string.calendar_not_selected
                            else R.string.calendar_preferred_unavailable
                        ),
                    "destination_calendar",
                ) {
                    focus.clearFocus()
                    onCalendar()
                }
                Text(
                    stringResource(R.string.calendar_preferred_note),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (calendar?.localOnly == true)
                    Text(
                        stringResource(R.string.calendar_local_only),
                        style = MaterialTheme.typography.bodySmall,
                    )
            }
            item {
                SettingChoice(
                    stringResource(R.string.reminder_setting),
                    reminderLabel(reminder),
                    "reminder_setting",
                ) {
                    focus.clearFocus()
                    choosingReminder = true
                }
            }
            item {
                SettingChoice(
                    stringResource(R.string.work_color),
                    colorLabel(workColor),
                    "work_color",
                    workColor,
                ) {
                    focus.clearFocus()
                    choosingColor = 1
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                stringResource(R.string.save_rest_days),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.save_rest_days_note),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            saveRestDays,
                            { saveRestDays = it },
                            Modifier.testTag("save_rest_days"),
                        )
                    }
                }
            }
            item {
                SettingChoice(
                    stringResource(R.string.rest_color),
                    colorLabel(restColor),
                    "rest_color",
                    restColor,
                ) {
                    focus.clearFocus()
                    choosingColor = 2
                }
            }
            item {
                Text(
                    stringResource(R.string.calendar_preferences_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { UpdateSettings(state.updater, vm) }
        }
        ActionFooter(
            stringResource(R.string.save),
            enabled = !state.busy,
            note = if (invalid) stringResource(R.string.invalid_name) else null,
            action = {
                invalid = name.isBlank() || name.trim().length > 160
                if (!invalid)
                    vm.saveSettings(
                        name,
                        state.settings.copy(
                            reminderMinutes = reminder,
                            eventColor = workColor,
                            restColor = restColor,
                            saveRestDays = saveRestDays,
                        ),
                        onSaved,
                    )
            },
        )
    }
    if (choosingReminder) {
        val choices = (reminderChoices + reminder).distinct()
        AlertDialog(
            onDismissRequest = { choosingReminder = false },
            title = { Text(stringResource(R.string.reminder_setting)) },
            text = {
                LazyColumn {
                    items(choices) { minutes ->
                        val label = reminderLabel(minutes)
                        TextButton(
                            onClick = {
                                reminder = minutes
                                choosingReminder = false
                            },
                            Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(reminder == minutes, onClick = null)
                            Text(label, Modifier.weight(1f).padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingReminder = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
    if (choosingColor != 0) {
        val selected = if (choosingColor == 1) workColor else restColor
        val choices = (listOf<Int?>(null) + eventColors.map { it.first } + selected).distinct()
        AlertDialog(
            onDismissRequest = { choosingColor = 0 },
            title = {
                Text(
                    stringResource(
                        if (choosingColor == 1) R.string.work_color else R.string.rest_color
                    )
                )
            },
            text = {
                LazyColumn {
                    items(choices) { color ->
                        TextButton(
                            onClick = {
                                if (choosingColor == 1) workColor = color else restColor = color
                                choosingColor = 0
                            },
                            Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(selected == color, onClick = null)
                            color?.let {
                                Box(
                                    Modifier.padding(horizontal = 8.dp)
                                        .size(20.dp)
                                        .background(Color(it), CircleShape)
                                )
                            }
                            Text(colorLabel(color), Modifier.weight(1f).padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingColor = 0 }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun SettingChoice(
    title: String,
    value: String,
    tag: String,
    color: Int? = null,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, Modifier.fillMaxWidth().testTag(tag)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            color?.let { Box(Modifier.size(28.dp).background(Color(it), CircleShape)) }
        }
    }
}
