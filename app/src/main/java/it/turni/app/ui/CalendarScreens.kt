package it.turni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.turni.app.R
import it.turni.app.calendar.WritableCalendar
import it.turni.app.calendar.preferredCalendar
import it.turni.core.ChangeKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(
    state: AppState,
    onChoose: (Long, Boolean) -> Unit,
    onRetry: () -> Unit,
    importing: Boolean = true,
) {
    var pending by remember { mutableStateOf<WritableCalendar?>(null) }
    fun choose(calendar: WritableCalendar) {
        if (!calendar.visible || !calendar.syncEnabled) pending = calendar
        else onChoose(calendar.id, false)
    }
    pending?.let { calendar ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.calendar_enable_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.calendar_enable_message,
                        calendar.name,
                        calendar.account,
                    ),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        onChoose(calendar.id, true)
                    }
                ) {
                    Text(stringResource(R.string.calendar_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    val viewer = calendarViewerLabel()
    val groups =
        state.calendars.groupBy {
            Triple(
                if (it.localOnly) "" else it.accountType,
                if (it.localOnly) "" else it.account,
                it.localOnly,
            )
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            if (importing) ImportProgress(3)
            Text(
                stringResource(R.string.choose_calendar),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (
                state.settings.calendarId != null &&
                    preferredCalendar(state.calendars, state.settings.calendarId) == null
            )
                Text(stringResource(R.string.calendar_preferred_unavailable))
            OutlinedCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.calendar_viewer),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(viewer, style = MaterialTheme.typography.titleMedium)
                }
            }
            TextButton(onClick = onRetry, enabled = !state.busy) {
                Text(stringResource(R.string.calendar_refresh))
            }
        }
        if (state.calendars.isEmpty()) item { Text(stringResource(R.string.no_calendar)) }
        groups.values.forEach { calendars ->
            val first = calendars.first()
            item(key = "account_${first.id}") {
                Column(
                    Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(calendarService(first), style = MaterialTheme.typography.titleMedium)
                    if (!first.localOnly && first.account.isNotBlank())
                        Text(first.account, style = MaterialTheme.typography.bodyMedium)
                    if (first.localOnly)
                        Text(
                            stringResource(R.string.calendar_local_short),
                            style = MaterialTheme.typography.bodySmall,
                        )
                }
            }
            items(calendars, key = { it.id }) { calendar ->
                OutlinedCard(
                    onClick = { choose(calendar) },
                    modifier =
                        Modifier.fillMaxWidth()
                            .testTag("calendar_option_${calendar.id}")
                            .semantics { selected = state.settings.calendarId == calendar.id },
                    enabled = calendar.writable && !state.busy,
                    colors =
                        CardDefaults.outlinedCardColors(
                            containerColor =
                                if (state.settings.calendarId == calendar.id)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                        ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = state.settings.calendarId == calendar.id,
                            onClick = null,
                            enabled = calendar.writable && !state.busy,
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                calendarName(calendar),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (!calendar.writable)
                                Text(
                                    stringResource(R.string.calendar_read_only_short),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            if (!calendar.visible)
                                Text(
                                    stringResource(R.string.calendar_hidden),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            if (!calendar.syncEnabled)
                                Text(
                                    stringResource(R.string.calendar_sync_off),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                        }
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
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) { CalendarDestination(calendar) }
                        }
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
