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
import it.turni.app.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    state: AppState,
    camera: () -> Unit,
    gallery: () -> Unit,
    settings: () -> Unit,
    history: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                stringResource(R.string.import_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        item { Text(state.profile?.name ?: stringResource(R.string.profile_help)) }
        item {
            Text(stringResource(R.string.local_note), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Button(onClick = camera, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.camera))
            }
        }
        item {
            OutlinedButton(onClick = gallery, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.gallery))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = history, Modifier.weight(1f)) {
                    Text(stringResource(R.string.history))
                }
                OutlinedButton(onClick = settings, Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings))
                }
            }
        }
        item {
            Text(stringResource(R.string.upcoming), style = MaterialTheme.typography.titleLarge)
        }
        if (state.upcoming.isEmpty()) item { Text(stringResource(R.string.no_shifts)) }
        items(state.upcoming, key = { it.id }) { shift -> ShiftSummary(shift) }
    }
}

@Composable
private fun ShiftSummary(shift: ShiftEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(shift.date, style = MaterialTheme.typography.titleMedium)
            Text(
                if (shift.type == "WORK_SHIFT") "${shift.startTime} → ${shift.endTime}"
                else shift.code.orEmpty()
            )
            if (shift.endNextDay) Text(stringResource(R.string.overnight))
            Text(
                stringResource(R.string.event_id, shift.calendarId, shift.calendarEventId),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun HistoryScreen(weeks: List<WeekEntity>, onWeek: (WeekEntity) -> Unit) {
    LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineMedium)
        }
        if (weeks.isEmpty()) item { Text(stringResource(R.string.no_shifts)) }
        items(weeks, key = { it.id }) { week ->
            Card(onClick = { onWeek(week) }, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.week_label, week.weekStart),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            R.string.imported_at,
                            Instant.ofEpochMilli(week.importedAt)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SavedWeekScreen(shifts: List<ShiftEntity>) {
    LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.source_not_saved)) }
        items(shifts, key = { it.id }) { ShiftSummary(it) }
    }
}

@Composable
fun ResultScreen(count: Int, onHome: () -> Unit) {
    ScreenColumn {
        Text(stringResource(R.string.sync_result), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.sync_count, count))
        Text(stringResource(R.string.done_note))
        Button(onClick = onHome) { Text(stringResource(R.string.home)) }
    }
}
