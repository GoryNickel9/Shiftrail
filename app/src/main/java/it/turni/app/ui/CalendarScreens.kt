package it.turni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.turni.app.R
import it.turni.core.ChangeKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(state: AppState, onChoose: (Long) -> Unit, onRetry: () -> Unit) {
    LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ImportProgress(3)
            Text(
                stringResource(R.string.choose_calendar),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(stringResource(R.string.calendar_help))
        }
        if (state.calendars.isEmpty())
            item {
                Text(stringResource(R.string.no_calendar))
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        items(state.calendars, key = { it.id }) { calendar ->
            Card(onClick = { onChoose(calendar.id) }, Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp)) {
                    RadioButton(
                        selected = state.settings.calendarId == calendar.id,
                        onClick = { onChoose(calendar.id) },
                    )
                    Column {
                        Text(calendar.name, style = MaterialTheme.typography.titleMedium)
                        Text(calendar.account)
                        if (calendar.localOnly) Text(stringResource(R.string.calendar_local_only))
                    }
                }
            }
        }
    }
}

@Composable
fun SyncScreen(state: AppState, vm: AppViewModel, synchronize: () -> Unit) {
    val plan = state.prepared ?: return
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ImportProgress(3)
                Text(
                    stringResource(R.string.sync_preview),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(stringResource(R.string.sync_help))
                state.calendars
                    .firstOrNull { it.id == plan.calendar }
                    ?.let { calendar ->
                        Text(calendar.name, style = MaterialTheme.typography.titleMedium)
                        Text(calendar.account)
                        if (calendar.localOnly) Text(stringResource(R.string.calendar_local_only))
                    }
            }
            items(plan.changes, key = { it.date.toString() }) { change ->
                val title =
                    when (change.kind) {
                        ChangeKind.INSERT -> R.string.insert_event
                        ChangeKind.UPDATE -> R.string.update_event
                        ChangeKind.UNCHANGED -> R.string.unchanged_event
                        ChangeKind.MISSING -> R.string.missing_event
                        ChangeKind.RECREATE -> R.string.recreate_event
                    }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp)) {
                        if (change.kind != ChangeKind.UNCHANGED)
                            Checkbox(
                                change.date in state.selectedChanges,
                                { vm.toggleChange(change.date) },
                            )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                change.date.toString(),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(stringResource(title))
                            val old = plan.saved.firstOrNull { it.date == change.date.toString() }
                            if (old != null && change.kind != ChangeKind.UNCHANGED)
                                Text(
                                    stringResource(
                                        R.string.before,
                                        old.code ?: "${old.startTime} → ${old.endTime}",
                                    )
                                )
                            change.incoming?.let { incoming ->
                                val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
                                Text(
                                    stringResource(
                                        R.string.after,
                                        if (incoming.allDay) incoming.title
                                        else
                                            "${Instant.ofEpochMilli(incoming.startMillis).atZone(ZoneId.of(incoming.timezone)).format(formatter)} → ${Instant.ofEpochMilli(incoming.endMillis).atZone(ZoneId.of(incoming.timezone)).format(formatter)}",
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        ActionFooter(
            stringResource(R.string.sync_button),
            enabled = !state.busy,
            action = synchronize,
        )
    }
}
