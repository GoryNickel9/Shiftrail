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
import androidx.compose.ui.res.painterResource
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
    val monthLabel = month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ITALIAN))
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
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { weekIndex = -1 },
                            modifier = Modifier.weight(1f).testTag("whole_month"),
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor =
                                        if (weekIndex < 0)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                                ),
                        ) {
                            Text(stringResource(R.string.period_month))
                        }
                        FilledTonalButton(
                            onClick = { choosingWeek = true },
                            modifier = Modifier.weight(1f).testTag("choose_week"),
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor =
                                        if (weekIndex >= 0)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                                ),
                        ) {
                            Text(
                                if (weekIndex < 0) stringResource(R.string.period_week)
                                else
                                    "${window.from.format(dateFormat)} – ${window.until.format(dateFormat)}"
                            )
                        }
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
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HoursTotalColumn(
                                    stringResource(R.string.hours_work),
                                    summary.workMinutes,
                                    "hours_work",
                                    Modifier.weight(1f),
                                )
                                HoursTotalColumn(
                                    stringResource(R.string.hours_extra),
                                    summary.extraMinutes,
                                    "hours_extra",
                                    Modifier.weight(1f),
                                )
                                HoursTotalColumn(
                                    stringResource(R.string.night_subset),
                                    summary.nightMinutes,
                                    "hours_night",
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
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
                            Row(
                                Modifier.fillMaxWidth().testTag("hours_actions_${period.id}"),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                IconButton(
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
                                    Icon(
                                        painterResource(R.drawable.ic_pen),
                                        stringResource(R.string.edit),
                                    )
                                }
                                if (extra != null)
                                    IconButton(
                                        modifier = Modifier.testTag("delete_${period.id}"),
                                        enabled = !state.busy,
                                        onClick = {
                                            if (canWriteCalendar()) deletingId = extra.id
                                            else {
                                                pendingDeleteId = extra.id
                                                permissions.launch(
                                                    calendarPermissions.toTypedArray()
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.ic_trash),
                                            stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                            }
                            Text(
                                period.date.format(
                                    DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
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

                            period.endDate?.let { endDate ->
                                if (endDate > period.date.plusDays(1))
                                    Text(
                                        stringResource(R.string.hours_ends_on, endDate.toString()),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                            }
                            Text(durationLabel(entry.minutes))
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
        WeekPicker(month, weekIndex, state.hoursPeriods, { choosingWeek = false }) {
            weekIndex = it
            choosingWeek = false
        }
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
private fun HoursTotalColumn(title: String, minutes: Long, tag: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Text(
            durationLabel(minutes),
            Modifier.testTag(tag),
            style = MaterialTheme.typography.titleSmall,
        )
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
                                    .format(DateTimeFormatter.ofPattern("LLLL", Locale.ITALIAN))
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
    var choosingTime by rememberSaveable { mutableStateOf<String?>(null) }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeField(
                            start,
                            stringResource(R.string.start_time),
                            "extra_start",
                            Modifier.weight(1f),
                            enabled = !busy,
                        ) {
                            choosingTime = "start"
                        }
                        TimeField(
                            end,
                            stringResource(R.string.end_time),
                            "extra_end",
                            Modifier.weight(1f),
                            enabled = !busy,
                        ) {
                            choosingTime = "end"
                        }
                    }
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
                            runCatching {
                                val parts = value.trim().split(":")
                                ShiftParser.clockTime(parts[0].toInt(), parts[1].toInt())
                            }
                                .getOrNull()
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
    choosingTime?.let { field ->
        TimeWheelDialog(
            value =
                runCatching { LocalTime.parse(if (field == "start") start else end) }
                    .getOrDefault(LocalTime.of(8, 0)),
            onDismiss = { choosingTime = null },
            onChoose = { time ->
                val formatted = time.format(DateTimeFormatter.ofPattern("HH:mm"))
                if (field == "start") start = formatted else end = formatted
                invalid = false
                choosingTime = null
            },
        )
    }
}
