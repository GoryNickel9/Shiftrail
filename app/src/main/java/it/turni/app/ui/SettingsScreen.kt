package it.turni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.turni.app.R
import it.turni.app.data.MappingEntity
import it.turni.core.CalendarBehavior
import java.time.LocalTime

@Composable
fun SettingsScreen(state: AppState, vm: AppViewModel, onSaved: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(state.profile?.name.orEmpty()) }
    var aliases by rememberSaveable { mutableStateOf(state.profile?.aliases.orEmpty()) }
    var title by rememberSaveable { mutableStateOf(state.settings.title) }
    var location by rememberSaveable { mutableStateOf(state.settings.location) }
    var description by rememberSaveable { mutableStateOf(state.settings.description) }
    var reminder by rememberSaveable {
        mutableStateOf(state.settings.reminderMinutes?.toString().orEmpty())
    }
    var color by rememberSaveable {
        mutableStateOf(state.settings.eventColor?.let { "#%06X".format(it and 0xffffff) }.orEmpty())
    }
    var developer by rememberSaveable { mutableStateOf(state.settings.developer) }
    var mappingDialog by remember { mutableStateOf<MappingEntity?>(null) }
    var invalid by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.profile_section),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(R.string.profile_help))
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                aliases,
                { aliases = it },
                label = { Text(stringResource(R.string.aliases)) },
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.event_section),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                title,
                { title = it },
                label = { Text(stringResource(R.string.event_title)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                location,
                { location = it },
                label = { Text(stringResource(R.string.location)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                description,
                { description = it },
                label = { Text(stringResource(R.string.description)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                reminder,
                { reminder = it },
                label = { Text(stringResource(R.string.reminder)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            TextButton(onClick = { advanced = !advanced }) {
                Text(stringResource(R.string.advanced_section))
            }
            if (advanced) {
                OutlinedTextField(
                    color,
                    { color = it },
                    label = { Text(stringResource(R.string.color)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    Switch(developer, { developer = it })
                    Text(stringResource(R.string.developer), Modifier.padding(12.dp))
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.legend), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.legend_help))
            state.mappings.forEach { mapping ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { mappingDialog = mapping }, Modifier.weight(1f)) {
                        Text("${mapping.code} = ${mapping.meaning}")
                    }
                    TextButton(onClick = { vm.removeMapping(mapping.code) }) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    mappingDialog = MappingEntity("", "", CalendarBehavior.IGNORE.name, null, null)
                }
            ) {
                Text(stringResource(R.string.add_code))
            }
            Text(stringResource(R.string.privacy), style = MaterialTheme.typography.bodySmall)
        }
        ActionFooter(
            stringResource(R.string.save),
            enabled = !state.busy,
            note = if (invalid) stringResource(R.string.validation_error) else null,
            action = {
                val minutes = if (reminder.isBlank()) null else reminder.toIntOrNull()
                val parsedColor =
                    if (color.isBlank()) null
                    else runCatching { android.graphics.Color.parseColor(color) }.getOrNull()
                invalid =
                    name.isBlank() ||
                        title.isBlank() ||
                        (reminder.isNotBlank() && (minutes == null || minutes !in 0..10080)) ||
                        (color.isNotBlank() &&
                            (!Regex("^#[0-9a-fA-F]{6}$").matches(color) || parsedColor == null))
                if (!invalid)
                    vm.saveSettings(
                        name,
                        aliases,
                        state.settings.copy(
                            title = title,
                            location = location,
                            description = description,
                            reminderMinutes = minutes,
                            eventColor = parsedColor,
                            developer = developer,
                        ),
                        onSaved,
                    )
            },
        )
    }
    mappingDialog?.let { mapping ->
        MappingDialog(
            mapping,
            dismiss = { mappingDialog = null },
            save = {
                vm.saveMapping(it)
                mappingDialog = null
            },
        )
    }
}

@Composable
private fun MappingDialog(
    mapping: MappingEntity,
    dismiss: () -> Unit,
    save: (MappingEntity) -> Unit,
) {
    var code by remember { mutableStateOf(mapping.code) }
    var meaning by remember { mutableStateOf(mapping.meaning) }
    var behavior by remember { mutableStateOf(CalendarBehavior.valueOf(mapping.behavior)) }
    var start by remember { mutableStateOf(mapping.startTime ?: "09:00") }
    var end by remember { mutableStateOf(mapping.endTime ?: "17:00") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.add_code)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    code,
                    { code = it },
                    label = { Text(stringResource(R.string.code)) },
                )
                OutlinedTextField(
                    meaning,
                    { meaning = it },
                    label = { Text(stringResource(R.string.meaning)) },
                )
                CalendarBehavior.entries.forEach { item ->
                    Row {
                        RadioButton(behavior == item, { behavior = item })
                        Text(
                            stringResource(
                                when (item) {
                                    CalendarBehavior.IGNORE -> R.string.ignore
                                    CalendarBehavior.ALL_DAY -> R.string.all_day
                                    CalendarBehavior.CUSTOM -> R.string.custom_time
                                }
                            ),
                            Modifier.padding(top = 12.dp),
                        )
                    }
                }
                if (behavior == CalendarBehavior.CUSTOM) {
                    OutlinedTextField(
                        start,
                        { start = it },
                        label = { Text(stringResource(R.string.start_time)) },
                    )
                    OutlinedTextField(
                        end,
                        { end = it },
                        label = { Text(stringResource(R.string.end_time)) },
                    )
                }
                if (invalid)
                    Text(
                        stringResource(R.string.invalid_cell),
                        color = MaterialTheme.colorScheme.error,
                    )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val first = runCatching { LocalTime.parse(start) }.getOrNull()
                    val last = runCatching { LocalTime.parse(end) }.getOrNull()
                    invalid =
                        !Regex("^[A-Za-z][A-Za-z. ]{0,23}$").matches(code) ||
                            meaning.isBlank() ||
                            (behavior == CalendarBehavior.CUSTOM &&
                                (first == null || last == null || first == last))
                    if (!invalid)
                        save(
                            MappingEntity(
                                code,
                                meaning,
                                behavior.name,
                                if (behavior == CalendarBehavior.CUSTOM) first?.toString()
                                else null,
                                if (behavior == CalendarBehavior.CUSTOM) last?.toString() else null,
                            )
                        )
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
