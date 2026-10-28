package it.turni.app.ui

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.turni.app.R
import it.turni.app.data.*
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    state: AppState,
    camera: () -> Unit,
    gallery: () -> Unit,
    settings: () -> Unit,
) {
    val next = state.upcoming.firstOrNull { it.type == "WORK_SHIFT" }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                stringResource(R.string.brand_tagline),
                style = MaterialTheme.typography.headlineLarge,
            )
        }
        if (next != null)
            item {
                Card(
                    Modifier.fillMaxWidth().testTag("home_illustration"),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                ) {
                    Column(
                        Modifier.padding(20.dp).testTag("home_shift_${next.id}"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.next_shift),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            "${next.startTime} – ${next.endTime}",
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(
                            java.time.LocalDate.parse(next.date)
                                .format(
                                    DateTimeFormatter.ofPattern(
                                        "EEEE d MMMM",
                                        java.util.Locale.ITALIAN,
                                    )
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        item {
            if (state.profile == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.first_setup),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(stringResource(R.string.profile_help))
                        Button(onClick = settings, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.setup_button))
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.import_for, state.profile.name),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Button(onClick = camera, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(
                        androidx.compose.ui.res.painterResource(R.drawable.ic_camera),
                        null,
                        Modifier.padding(end = 10.dp),
                    )
                    Text(stringResource(R.string.camera))
                }
                OutlinedButton(
                    onClick = gallery,
                    Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 52.dp),
                ) {
                    Icon(
                        androidx.compose.ui.res.painterResource(R.drawable.ic_image),
                        null,
                        Modifier.padding(end = 10.dp),
                    )
                    Text(stringResource(R.string.gallery))
                }
            }
        }
        item {
            Text(stringResource(R.string.upcoming), style = MaterialTheme.typography.titleLarge)
        }
        if (state.upcoming.isEmpty())
            item {
                Text(
                    stringResource(R.string.empty_upcoming),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        items(state.upcoming.filter { it.id != next?.id }, key = { it.id }) { shift ->
            ShiftSummary(shift)
        }
    }
}

@Composable
private fun ShiftSummary(shift: ShiftEntity) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("home_shift_${shift.id}")) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val date = java.time.LocalDate.parse(shift.date)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEE", java.util.Locale.ITALIAN)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(
                        if (shift.type == "WORK_SHIFT") R.string.default_event_title
                        else R.string.rest_day
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    if (shift.type == "WORK_SHIFT") "${shift.startTime} – ${shift.endTime}"
                    else shift.code.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (shift.endNextDay)
                    Text(
                        date
                            .plusDays(1)
                            .format(
                                DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.ITALIAN)
                            ),
                        style = MaterialTheme.typography.bodySmall,
                    )
            }
        }
    }
}

@Composable
fun ResultScreen(state: AppState, onError: (String) -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    ScreenColumn {
        Text(stringResource(R.string.sync_result), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.sync_count, state.syncCount ?: 0))
        state.syncedCalendar?.let { calendar ->
            Text(calendar.name, style = MaterialTheme.typography.titleMedium)
            Text(calendar.account)
            if (calendar.localOnly) Text(stringResource(R.string.calendar_local_only))
        }
        Text(stringResource(R.string.done_note))
        state.syncedShift?.let { shift ->
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                ContentUris.withAppendedId(
                                    CalendarContract.Events.CONTENT_URI,
                                    shift.calendarEventId,
                                ),
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        onError(context.getString(R.string.calendar_app_missing))
                    } catch (_: SecurityException) {
                        onError(context.getString(R.string.permission_denied))
                    }
                }
            ) {
                Text(stringResource(R.string.open_calendar))
            }
        }
        Button(onClick = onHome) { Text(stringResource(R.string.home)) }
    }
}
