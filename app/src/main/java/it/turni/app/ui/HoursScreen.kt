package it.turni.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import it.turni.app.R
import it.turni.core.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HoursScreen(state: AppState, vm: AppViewModel, onSettings: () -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var weekIndex by rememberSaveable(monthText) { mutableIntStateOf(-1) }
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    var choosingWeek by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingEditId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAdd by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = state.hoursPeriods.firstOrNull { it.id == editingId }
    val deleting = state.hoursExtras.firstOrNull { it.id == deletingId }
    val context = LocalContext.current
    val calendarPermissions =
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            result ->
            if (calendarPermissions.all { result[it] == true }) {
                editingId = pendingEditId
                adding = pendingAdd
                deletingId = pendingDeleteId
                vm.refreshCalendar()
            } else vm.reportError(context.getString(R.string.permission_denied))
            pendingEditId = null
            pendingAdd = false
            pendingDeleteId = null
        }
    fun canWriteCalendar() = calendarPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(monthText, state.profile?.id) { vm.loadHours(month) }
    val weeks = WorkingHours.monthWeeks(month)
    val window = weeks.getOrNull(weekIndex) ?: HoursWindow(month.atDay(1), month.atEndOfMonth())
    val summary = WorkingHours.summarize(state.hoursPeriods, window)
    val dateFormat = DateTimeFormatter.ofPattern("dd/MM")
    val monthLabel =
        month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
    val previousMonth = stringResource(R.string.previous_month)
    val nextMonth = stringResource(R.string.next_month)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.hours),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(R.string.hours_note),
                    Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.profile == null)
                item {
                    Text(stringResource(R.string.profile_help))
                    Button(onClick = onSettings) { Text(stringResource(R.string.setup_button)) }
                }
            else {
                if (!state.hoursCalendarAvailable && state.hoursLoaded)
                    item {
                        Text(stringResource(R.string.hours_calendar_cached))
                        TextButton(
                            onClick = { permissions.launch(calendarPermissions.toTypedArray()) }
                        ) {
                            Text(stringResource(R.string.hours_calendar_permission))
                        }
                    }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { monthText = month.minusMonths(1).toString() },
                            enabled = month.year > 1900 || month.monthValue > 1,
                            modifier =
                                Modifier.testTag("previous_month").semantics {
                                    contentDescription = previousMonth
                                },
                        ) {
                            Text("‹", style = MaterialTheme.typography.headlineMedium)
                        }
                        OutlinedButton(
                            onClick = { choosingMonth = true },
                            Modifier.weight(1f).testTag("choose_month"),
                        ) {
                            Text(monthLabel)
                        }
                        IconButton(
                            onClick = { monthText = month.plusMonths(1).toString() },
                            enabled = month.year < 2100 || month.monthValue < 12,
                            modifier =
                                Modifier.testTag("next_month").semantics {
                                    contentDescription = nextMonth
                                },
                        ) {
                            Text("›", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    OutlinedButton(
                        onClick = { choosingWeek = true },
                        Modifier.fillMaxWidth().testTag("choose_week"),
                    ) {
                        Text(
                            if (weekIndex < 0) stringResource(R.string.hours_month)
                            else
                                stringResource(
                                    R.string.hours_week,
                                    window.from.format(dateFormat),
                                    window.until.format(dateFormat),
                                )
                        )
                    }
                }
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                stringResource(R.string.hours_total),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (!state.hoursLoaded) CircularProgressIndicator()
                            else
                                Text(
                                    durationLabel(summary.totalMinutes),
                                    Modifier.testTag("hours_total"),
                                    style = MaterialTheme.typography.headlineLarge,
                                )
                            HorizontalDivider()
                            HoursTotalLine(
                                stringResource(R.string.hours_work),
                                summary.workMinutes,
                                "hours_work",
                            )
                            HoursTotalLine(
                                stringResource(R.string.hours_extra),
                                summary.extraMinutes,
                                "hours_extra",
                            )
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.hours_boundary_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.hoursLoaded && summary.entries.isEmpty())
                    item { Text(stringResource(R.string.hours_empty)) }
                items(summary.entries, key = { it.period.id }) { entry ->
                    val period = entry.period
                    val extra = state.hoursExtras.firstOrNull { it.id == period.id }
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                period.date.format(
                                    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    if (period.source == WorkSource.EXTRA) R.string.extra_work
                                    else R.string.default_event_title
                                )
                            )
                            Text(
                                "${period.start} → ${period.end}",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (period.end < period.start)
                                Text(
                                    stringResource(R.string.overnight),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            period.endDate?.let { endDate ->
                                if (endDate > period.date.plusDays(1))
                                    Text(
                                        stringResource(R.string.hours_ends_on, endDate.toString()),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                            }
                            Text(durationLabel(entry.minutes))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        if (canWriteCalendar()) editingId = period.id
                                        else {
                                            pendingEditId = period.id
                                            permissions.launch(calendarPermissions.toTypedArray())
                                        }
                                    },
                                    enabled = !state.busy,
                                    modifier = Modifier.testTag("edit_${period.id}"),
                                ) {
                                    Text(stringResource(R.string.edit))
                                }
                                if (extra != null)
                                    TextButton(
                                        onClick = {
                                            if (canWriteCalendar()) deletingId = extra.id
                                            else {
                                                pendingDeleteId = extra.id
                                                permissions.launch(
                                                    calendarPermissions.toTypedArray()
                                                )
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.delete))
                                    }
                            }
                        }
                    }
                }
            }
        }
        if (state.profile != null)
            ActionFooter(
                stringResource(R.string.add_extra),
                enabled = !state.busy,
                action = {
                    if (canWriteCalendar()) adding = true
                    else {
                        pendingAdd = true
                        permissions.launch(calendarPermissions.toTypedArray())
                    }
                },
            )
    }
    if (choosingMonth)
        MonthDialog(month, { choosingMonth = false }) {
            monthText = it.toString()
            choosingMonth = false
        }
    if (choosingWeek)
        AlertDialog(
            onDismissRequest = { choosingWeek = false },
            title = { Text(stringResource(R.string.hours)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            weekIndex = -1
                            choosingWeek = false
                        }
                    ) {
                        Text(stringResource(R.string.hours_month))
                    }
                    weeks.forEachIndexed { index, week ->
                        TextButton(
                            onClick = {
                                weekIndex = index
                                choosingWeek = false
                            }
                        ) {
                            Text(
                                stringResource(
                                    R.string.hours_week,
                                    week.from.format(dateFormat),
                                    week.until.format(dateFormat),
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingWeek = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    if (adding || editing != null)
        WorkHoursDialog(
            editing,
            month,
            state.busy,
            {
                adding = false
                editingId = null
            },
        ) { date, start, end ->
            if (editing?.source == WorkSource.SCHEDULE)
                vm.saveShiftHours(editing, start, end) { editingId = null }
            else
                vm.saveExtra(
                    date,
                    start,
                    end,
                    state.hoursExtras.firstOrNull { it.id == editingId },
                ) {
                    monthText = YearMonth.from(date).toString()
                    weekIndex = -1
                    adding = false
                    editingId = null
                }
        }
    deleting?.let { extra ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.extra_delete_title)) },
            text = { Text("${extra.date} · ${extra.startTime} → ${extra.endTime}") },
            confirmButton = {
                TextButton(
                    onClick = { vm.removeExtra(extra) { deletingId = null } },
                    enabled = !state.busy,
                    modifier = Modifier.testTag("extra_delete_confirm"),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun durationLabel(minutes: Long) =
    stringResource(R.string.hours_duration, minutes / 60, minutes % 60)

@Composable
private fun HoursTotalLine(title: String, minutes: Long, tag: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f))
        Text(durationLabel(minutes), Modifier.testTag(tag))
    }
}

@Composable
private fun MonthDialog(month: YearMonth, onDismiss: () -> Unit, onChoose: (YearMonth) -> Unit) {
    var year by rememberSaveable { mutableIntStateOf(month.year) }
    val previousYear = stringResource(R.string.previous_year)
    val nextYear = stringResource(R.string.next_year)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_month)) },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = { year-- },
                        enabled = year > 1900,
                        modifier = Modifier.semantics { contentDescription = previousYear },
                    ) {
                        Text("‹")
                    }
                    Text(year.toString(), style = MaterialTheme.typography.titleLarge)
                    TextButton(
                        onClick = { year++ },
                        enabled = year < 2100,
                        modifier = Modifier.semantics { contentDescription = nextYear },
                    ) {
                        Text("›")
                    }
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items((1..12).toList()) { number ->
                        TextButton(
                            onClick = { onChoose(YearMonth.of(year, number)) },
                            Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                YearMonth.of(year, number)
                                    .atDay(1)
                                    .format(
                                        DateTimeFormatter.ofPattern("LLLL", Locale.getDefault())
                                    )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun WorkHoursDialog(
    previous: WorkPeriod?,
    month: YearMonth,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (LocalDate, LocalTime, LocalTime) -> Unit,
) {
    var date by
        rememberSaveable(previous?.id) {
            mutableStateOf(
                previous?.date?.toString()
                    ?: (if (YearMonth.now() == month) LocalDate.now() else month.atDay(1))
                        .toString()
            )
        }
    var start by
        rememberSaveable(previous?.id) {
            mutableStateOf(previous?.start?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty())
        }
    var end by
        rememberSaveable(previous?.id) {
            mutableStateOf(previous?.end?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty())
        }
    val scheduled = previous?.source == WorkSource.SCHEDULE
    var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (scheduled) R.string.edit_shift_hours
                    else if (previous == null) R.string.add_extra else R.string.edit_extra
                )
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        date,
                        {
                            date = it
                            invalid = false
                        },
                        Modifier.fillMaxWidth().testTag("extra_date"),
                        label = { Text(stringResource(R.string.date)) },
                        singleLine = true,
                        readOnly = scheduled,
                    )
                }
                item {
                    OutlinedTextField(
                        start,
                        {
                            start = it
                            invalid = false
                        },
                        Modifier.fillMaxWidth().testTag("extra_start"),
                        label = { Text(stringResource(R.string.start_time)) },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        end,
                        {
                            end = it
                            invalid = false
                        },
                        Modifier.fillMaxWidth().testTag("extra_end"),
                        label = { Text(stringResource(R.string.end_time)) },
                        singleLine = true,
                    )
                }
                item {
                    Text(
                        stringResource(
                            if (scheduled) R.string.shift_hours_help else R.string.extra_help
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (invalid)
                    item {
                        Text(
                            stringResource(R.string.extra_invalid),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedDate = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
                    fun time(value: String) =
                        if (Regex("\\d{2}:\\d{2}").matches(value.trim()))
                            runCatching { LocalTime.parse(value.trim()) }.getOrNull()
                        else null
                    val parsedStart = time(start)
                    val parsedEnd = time(end)
                    invalid =
                        parsedDate == null ||
                            parsedDate.year !in 1900..2100 ||
                            parsedStart == null ||
                            parsedEnd == null ||
                            parsedStart == parsedEnd
                    if (!invalid)
                        onSave(
                            requireNotNull(parsedDate),
                            requireNotNull(parsedStart),
                            requireNotNull(parsedEnd),
                        )
                },
                enabled = !busy,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
