package it.turni.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.turni.app.R
import it.turni.app.payroll.*
import it.turni.core.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val payrollDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
private val payrollDateTime = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ITALIAN)

private fun payrollDuration(minutes: Long?) =
    minutes?.let { "${it/60} h ${"%02d".format(it%60)} min" } ?: "—"

private fun payrollClock(minutes: Int?) =
    minutes?.let { "%02d:%02d".format(it / 60, it % 60) }.orEmpty()

private fun payrollDelta(minutes: Long) =
    if (minutes < 60) "$minutes min"
    else if (minutes % 60 == 0L) "${minutes/60} h" else payrollDuration(minutes)

@Composable
fun PayrollScreen(
    appState: AppState,
    appVm: AppViewModel,
    initialMonth: YearMonth,
    vm: PayrollViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val profile = appState.profile
    LaunchedEffect(profile?.id, initialMonth) {
        profile?.let {
            vm.start(it.id, initialMonth)
            appVm.loadHours(initialMonth)
        }
    }
    val report = state.report
    var selectedDay by rememberSaveable(state.selected?.id) { mutableStateOf<Int?>(null) }
    var editingDay by rememberSaveable(state.selected?.id) { mutableStateOf<Int?>(null) }
    var filter by rememberSaveable(state.selected?.id) { mutableStateOf("issues") }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.import(uri)
        }
    val comparison =
        remember(
            report,
            appState.hoursPeriods,
            appState.hoursRestDates,
            appState.hoursLoaded,
            appState.hoursCalendarAvailable,
            appState.hoursReadError,
        ) {
            if (report != null && appState.hoursLoaded)
                PayrollComparator.compare(
                    report,
                    appState.hoursPeriods,
                    appState.hoursRestDates,
                    appState.hoursCalendarAvailable && !appState.hoursReadError,
                )
            else null
        }
    BackHandler(selectedDay != null) { selectedDay = null }
    LazyColumn(
        Modifier.fillMaxSize().testTag("payroll_list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                stringResource(
                    if (state.page == PayrollPage.REVIEW) R.string.payroll_review
                    else R.string.payroll_title
                ),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        state.error?.let { error -> item { PayrollNotice(error, error = true) } }
        if (state.busy)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        if (state.progress >= 0)
                            stringResource(R.string.payroll_loading, state.progress)
                        else stringResource(R.string.payroll_working),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        if (profile == null) item { Text(stringResource(R.string.profile_help)) }
        else
            when (state.page) {
                PayrollPage.UPLOAD -> {
                    item { Text(stringResource(R.string.payroll_intro)) }
                    item {
                        Button(
                            onClick = { picker.launch(arrayOf("application/pdf")) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().testTag("payroll_pick_pdf"),
                        ) {
                            Text(stringResource(R.string.payroll_upload))
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.payroll_format),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.payroll_privacy),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.reports.isNotEmpty())
                        item {
                            Text(
                                stringResource(R.string.payroll_history),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    items(state.reports, key = { it.id }) { entity ->
                        OutlinedCard(
                            onClick = {
                                selectedDay = null
                                vm.open(entity)
                            },
                            enabled = !state.busy,
                            modifier =
                                Modifier.fillMaxWidth().testTag("payroll_history_${entity.id}"),
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    YearMonth.parse(entity.month)
                                        .atDay(1)
                                        .format(
                                            DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ITALIAN)
                                        ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(R.string.payroll_revise),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                PayrollPage.REVIEW ->
                    if (report != null) {
                        item { PayrollReviewHeader(state, profile.name, vm) }
                        items(report.activeDays, key = { it.day }) { row ->
                            OutlinedCard(
                                Modifier.fillMaxWidth().testTag("payroll_reading_${row.day}")
                            ) {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        report.month.atDay(row.day).format(payrollDate),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        stringResource(R.string.payroll_document_total) +
                                            ": " +
                                            (row.minutes?.let { payrollDuration(it.toLong()) }
                                                ?: stringResource(R.string.payroll_no_data))
                                    )
                                    if (row.needsReview)
                                        Text(
                                            stringResource(R.string.payroll_reading_review),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    if (row.daylightSaving && !row.daylightSavingReviewed)
                                        Text(stringResource(R.string.payroll_dst_review))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { vm.showSource(row.day) },
                                            enabled = !state.busy,
                                        ) {
                                            Text(stringResource(R.string.payroll_show_row))
                                        }
                                        TextButton(
                                            onClick = {
                                                vm.dismissError()
                                                editingDay = row.day
                                            },
                                            enabled = !state.busy,
                                            modifier = Modifier.testTag("payroll_edit_${row.day}"),
                                        ) {
                                            Text(stringResource(R.string.payroll_edit_row))
                                        }
                                    }
                                }
                            }
                        }
                    }
                PayrollPage.RESULTS ->
                    if (report != null) {
                        val detail =
                            comparison?.days?.firstOrNull { it.date.dayOfMonth == selectedDay }
                        if (detail != null)
                            item {
                                PayrollDayDetail(
                                    detail,
                                    state.busy,
                                    { selectedDay = null },
                                    { vm.showSource(detail.date.dayOfMonth) },
                                    {
                                        vm.dismissError()
                                        editingDay = detail.date.dayOfMonth
                                    },
                                )
                            }
                        else {
                            item {
                                Text(
                                    report.month
                                        .atDay(1)
                                        .format(
                                            DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ITALIAN)
                                        ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            if (comparison == null) item { CircularProgressIndicator() }
                            else {
                                item { PayrollTotals(comparison) }
                                if (!report.totalMatches)
                                    item {
                                        PayrollNotice(
                                            stringResource(R.string.payroll_total_mismatch),
                                            error = true,
                                        )
                                    }
                                if (!appState.hoursCalendarAvailable || appState.hoursReadError)
                                    item {
                                        PayrollNotice(
                                            stringResource(R.string.payroll_calendar_warning)
                                        )
                                    }
                                item {
                                    PayrollNotice(
                                        if (
                                            comparison.differenceDays == 0 &&
                                                comparison.pendingDays == 0
                                        )
                                            stringResource(R.string.payroll_all_clear)
                                        else
                                            stringResource(
                                                if (comparison.pendingDays > 0)
                                                    R.string.payroll_counts_provisional
                                                else R.string.payroll_counts,
                                                comparison.differenceDays,
                                                comparison.pendingDays,
                                            )
                                    )
                                }
                                item {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = filter == "issues",
                                            onClick = { filter = "issues" },
                                            label = {
                                                Text(
                                                    stringResource(
                                                        R.string.payroll_filter_issues,
                                                        comparison.days.count {
                                                            it.differences.isNotEmpty()
                                                        },
                                                    )
                                                )
                                            },
                                            modifier = Modifier.testTag("payroll_filter_issues"),
                                        )
                                        FilterChip(
                                            selected = filter == "all",
                                            onClick = { filter = "all" },
                                            label = {
                                                Text(
                                                    stringResource(
                                                        R.string.payroll_filter_all,
                                                        comparison.days.size,
                                                    )
                                                )
                                            },
                                            modifier = Modifier.testTag("payroll_filter_all"),
                                        )
                                        FilterChip(
                                            selected = filter == "differences",
                                            onClick = { filter = "differences" },
                                            label = {
                                                Text(
                                                    stringResource(
                                                        R.string.payroll_filter_differences,
                                                        comparison.differenceDays,
                                                    )
                                                )
                                            },
                                            modifier =
                                                Modifier.testTag("payroll_filter_differences"),
                                        )
                                    }
                                }
                                items(
                                    comparison.days.filter {
                                        when (filter) {
                                            "all" -> true
                                            "differences" ->
                                                PayrollDifference.HOURS in it.differences ||
                                                    PayrollDifference.TIMES in it.differences
                                            else -> it.differences.isNotEmpty()
                                        }
                                    },
                                    key = { it.date.toString() },
                                ) { day ->
                                    PayrollDayCard(day, !state.busy) {
                                        selectedDay = day.date.dayOfMonth
                                    }
                                }
                                item {
                                    Text(
                                        stringResource(R.string.payroll_basis),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                item {
                                    Text(
                                        stringResource(R.string.payroll_program_note),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        selectedDay = null
                                        vm.review()
                                    },
                                    enabled = !state.busy,
                                    modifier = Modifier.testTag("payroll_revise"),
                                ) {
                                    Text(stringResource(R.string.payroll_revise))
                                }
                                OutlinedButton(
                                    onClick = {
                                        selectedDay = null
                                        vm.newImport()
                                    },
                                    enabled = !state.busy,
                                    modifier = Modifier.testTag("payroll_new"),
                                ) {
                                    Text(stringResource(R.string.payroll_new))
                                }
                            }
                        }
                    }
            }
        if (state.selected != null && state.page != PayrollPage.UPLOAD)
            item {
                TextButton(
                    onClick = { deleting = true },
                    enabled = !state.busy,
                    modifier = Modifier.testTag("payroll_delete"),
                ) {
                    Text(
                        stringResource(R.string.payroll_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
    }
    report
        ?.days
        ?.firstOrNull { it.day == editingDay }
        ?.let { row ->
            PayrollRowEditor(
                row,
                state.busy,
                state.error,
                {
                    editingDay = null
                    vm.dismissError()
                },
            ) { minutes, punches, dst, continued ->
                vm.saveDay(row.day, minutes, punches, dst, continued) { editingDay = null }
            }
        }
    if (state.previewLoading || state.preview != null)
        PayrollSourceDialog(state.preview, vm::dismissSource)
    if (deleting)
        AlertDialog(
            onDismissRequest = { if (!state.busy) deleting = false },
            title = { Text(stringResource(R.string.payroll_delete_question)) },
            text = { Text(stringResource(R.string.payroll_delete_note)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = false
                        selectedDay = null
                        vm.delete()
                    },
                    enabled = !state.busy,
                    modifier = Modifier.testTag("payroll_delete_confirm"),
                ) {
                    Text(stringResource(R.string.payroll_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }, enabled = !state.busy) {
                    Text(stringResource(R.string.payroll_cancel))
                }
            },
        )
}

@Composable
private fun PayrollReviewHeader(state: PayrollUiState, profileName: String, vm: PayrollViewModel) {
    val report = requireNotNull(state.report)
    var period by rememberSaveable(state.selected?.id) { mutableStateOf(report.month.toString()) }
    var total by
        rememberSaveable(state.selected?.id) { mutableStateOf(payrollClock(report.printedMinutes)) }
    var identity by
        rememberSaveable(state.selected?.id) { mutableStateOf(report.identityConfirmed) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = period,
            onValueChange = { period = it },
            label = { Text(stringResource(R.string.payroll_period)) },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag("payroll_period"),
        )
        Text(
            stringResource(
                R.string.payroll_person,
                report.employeeName ?: stringResource(R.string.payroll_unknown_person),
            )
        )
        Text(
            stringResource(R.string.payroll_profile, profileName),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!PayrollParser.matchesName(report.employeeName, EmployeeProfile(profileName)))
            PayrollNotice(stringResource(R.string.payroll_name_warning))
        TextButton(onClick = { vm.showSource() }, enabled = !state.busy) {
            Text(stringResource(R.string.payroll_show_header))
        }
        OutlinedTextField(
            value = total,
            onValueChange = { total = it },
            label = { Text(stringResource(R.string.payroll_printed_total)) },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag("payroll_printed_total"),
        )
        Text(
            stringResource(
                R.string.payroll_read_total,
                payrollDuration(report.readMinutes.toLong()),
            ),
            Modifier.testTag("payroll_read_total"),
        )
        TextButton(onClick = { vm.showSource(footer = true) }, enabled = !state.busy) {
            Text(stringResource(R.string.payroll_show_footer))
        }
        val entered = PayrollParser.duration(total)
        PayrollNotice(
            stringResource(
                if (report.complete && !report.outsideMonth && entered == report.readMinutes)
                    R.string.payroll_total_ok
                else R.string.payroll_total_mismatch
            ),
            error = entered != report.readMinutes,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = identity,
                onCheckedChange = { identity = it },
                enabled = !state.busy,
                modifier = Modifier.testTag("payroll_identity"),
            )
            Text(
                stringResource(R.string.payroll_identity_check),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Button(
            onClick = { vm.confirm(period, total, identity) },
            enabled = identity && !state.busy,
            modifier = Modifier.fillMaxWidth().testTag("payroll_compare"),
        ) {
            Text(stringResource(R.string.payroll_compare))
        }
    }
}

@Composable
private fun PayrollNotice(text: String, error: Boolean = false) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (error) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PayrollTotals(comparison: PayrollComparison) {
    Card(
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoxWithConstraints {
                if (maxWidth < 260.dp || LocalDensity.current.fontScale > 1.3f)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PayrollTotal(
                            stringResource(R.string.payroll_app_total),
                            comparison.appMinutes,
                            "payroll_app_total",
                        )
                        PayrollTotal(
                            stringResource(R.string.payroll_document_total),
                            comparison.documentMinutes.toLong(),
                            "payroll_document_total",
                        )
                    }
                else
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            PayrollTotal(
                                stringResource(R.string.payroll_app_total),
                                comparison.appMinutes,
                                "payroll_app_total",
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            PayrollTotal(
                                stringResource(R.string.payroll_document_total),
                                comparison.documentMinutes.toLong(),
                                "payroll_document_total",
                            )
                        }
                    }
            }
            if (comparison.pendingDays > 0)
                Text(
                    stringResource(R.string.payroll_partial),
                    style = MaterialTheme.typography.bodySmall,
                )
            val missing = comparison.days.count { PayrollDifference.MISSING_APP in it.differences }
            if (missing > 0)
                Text(
                    stringResource(R.string.payroll_missing_count, missing),
                    style = MaterialTheme.typography.bodySmall,
                )
        }
    }
}

@Composable
private fun PayrollTotal(label: String, minutes: Long, tag: String) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Text(
        payrollDuration(minutes),
        Modifier.testTag(tag),
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun payrollStatus(differences: Set<PayrollDifference>): String =
    stringResource(
        when {
            PayrollDifference.MISSING_APP in differences -> R.string.payroll_missing
            PayrollDifference.READING in differences -> R.string.payroll_reading_review
            PayrollDifference.INCOMPLETE_PUNCHES in differences -> R.string.payroll_punches_review
            PayrollDifference.DAYLIGHT_SAVING in differences -> R.string.payroll_dst_review
            PayrollDifference.APP_REVIEW in differences -> R.string.payroll_app_review
            PayrollDifference.WORK_DAY in differences -> R.string.payroll_work_day_review
            PayrollDifference.HOURS in differences -> R.string.payroll_hours_difference
            PayrollDifference.TIMES in differences -> R.string.payroll_times_difference
            else -> R.string.payroll_match
        }
    )

@Composable
private fun PayrollDayCard(day: PayrollComparisonDay, enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("payroll_day_${day.date}"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(day.date.format(payrollDate), style = MaterialTheme.typography.titleMedium)
            Text(
                payrollStatus(day.differences),
                color =
                    if (PayrollDifference.HOURS in day.differences) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                stringResource(
                    R.string.payroll_day_values,
                    day.appMinutes?.let { payrollDuration(it) }
                        ?: stringResource(R.string.payroll_no_data),
                    day.document.minutes?.let { payrollDuration(it.toLong()) }
                        ?: stringResource(R.string.payroll_no_data),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            day.deltaMinutes
                ?.takeIf { it != 0L && PayrollDifference.HOURS in day.differences }
                ?.let { delta ->
                    Text(
                        stringResource(
                            if (PayrollDifference.READING in day.differences) {
                                if (delta > 0) R.string.payroll_unverified_less
                                else R.string.payroll_unverified_more
                            } else if (delta > 0) R.string.payroll_less else R.string.payroll_more,
                            payrollDelta(abs(delta)),
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
        }
    }
}

@Composable
private fun PayrollDayDetail(
    day: PayrollComparisonDay,
    busy: Boolean,
    onBack: () -> Unit,
    onSource: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("payroll_back_to_month")) {
            Text(stringResource(R.string.payroll_return))
        }
        Text(
            day.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)),
            style = MaterialTheme.typography.titleLarge,
        )
        PayrollNotice(
            payrollStatus(day.differences),
            error = PayrollDifference.HOURS in day.differences,
        )
        day.deltaMinutes
            ?.takeIf { it != 0L && PayrollDifference.HOURS in day.differences }
            ?.let { delta ->
                Text(
                    stringResource(
                        if (PayrollDifference.READING in day.differences) {
                            if (delta > 0) R.string.payroll_unverified_less
                            else R.string.payroll_unverified_more
                        } else if (delta > 0) R.string.payroll_less else R.string.payroll_more,
                        payrollDelta(abs(delta)),
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        Text(
            stringResource(R.string.payroll_app_total) +
                ": " +
                (day.appMinutes?.let { payrollDuration(it) }
                    ?: stringResource(R.string.payroll_no_data)),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.payroll_document_total) +
                ": " +
                (day.document.minutes?.let { payrollDuration(it.toLong()) }
                    ?: stringResource(R.string.payroll_no_data)),
            style = MaterialTheme.typography.titleMedium,
        )
        if (PayrollDifference.MISSING_APP in day.differences)
            PayrollNotice(stringResource(R.string.payroll_no_app_data))
        if (PayrollDifference.READING in day.differences)
            PayrollNotice(stringResource(R.string.payroll_reading_provisional))
        if (PayrollDifference.INCOMPLETE_PUNCHES in day.differences)
            PayrollNotice(stringResource(R.string.payroll_unpaired))
        if (PayrollDifference.WORK_DAY in day.differences)
            PayrollNotice(stringResource(R.string.payroll_work_day_note))
        if (day.document.daylightSaving) PayrollNotice(stringResource(R.string.payroll_dst_note))
        Text(
            stringResource(R.string.payroll_app_intervals),
            style = MaterialTheme.typography.titleSmall,
        )
        PayrollIntervals(day.appIntervals)
        day.appPeriods
            .flatMap { it.validBreaks }
            .distinct()
            .forEach { pause ->
                Text(
                    stringResource(R.string.unpaid_breaks) +
                        ": " +
                        pause.start.format(payrollDateTime) +
                        " – " +
                        pause.end.format(payrollDateTime),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        Text(
            stringResource(R.string.payroll_document_intervals),
            style = MaterialTheme.typography.titleSmall,
        )
        PayrollIntervals(day.documentIntervals)
        OutlinedButton(
            onClick = onSource,
            enabled = !busy,
            modifier = Modifier.testTag("payroll_show_source"),
        ) {
            Text(stringResource(R.string.payroll_show_row))
        }
        Button(
            onClick = onEdit,
            enabled = !busy,
            modifier = Modifier.testTag("payroll_edit_detail"),
        ) {
            Text(stringResource(R.string.payroll_edit_row))
        }
        Text(
            stringResource(R.string.payroll_raw, day.document.raw),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PayrollIntervals(intervals: List<PayrollInterval>) {
    if (intervals.isEmpty())
        Text(
            stringResource(R.string.payroll_no_intervals),
            style = MaterialTheme.typography.bodyMedium,
        )
    intervals.forEach {
        Text(
            it.start.format(payrollDateTime) + " – " + it.end.format(payrollDateTime),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PayrollRowEditor(
    row: PayrollDay,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean, Set<Int>) -> Unit,
) {
    var amount by rememberSaveable(row.day) { mutableStateOf(payrollClock(row.minutes)) }
    var punches by
        rememberSaveable(row.day) {
            mutableStateOf(
                row.punches.joinToString("; ") {
                    (if (it.kind == PunchKind.ENTRY) "E " else "U ") + payrollClock(it.minute)
                }
            )
        }
    var checked by rememberSaveable(row.day) { mutableStateOf(false) }
    var dst by rememberSaveable(row.day) { mutableStateOf(row.daylightSavingReviewed) }
    var continued by
        rememberSaveable(row.day) {
            mutableStateOf(row.punches.filter { it.previousWorkDay }.map { it.minute })
        }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.payroll_edit_row)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.payroll_duration)) },
                    singleLine = true,
                    enabled = !busy,
                    isError = PayrollParser.duration(amount, 1440) == null,
                    modifier = Modifier.fillMaxWidth().testTag("payroll_edit_minutes"),
                )
                OutlinedTextField(
                    value = punches,
                    onValueChange = { punches = it },
                    label = { Text(stringResource(R.string.payroll_stamps)) },
                    enabled = !busy,
                    isError = PayrollParser.punches(punches) == null,
                    modifier = Modifier.fillMaxWidth().testTag("payroll_edit_stamps"),
                )
                Text(
                    stringResource(R.string.payroll_stamps_help),
                    style = MaterialTheme.typography.bodySmall,
                )
                val entries = PayrollParser.punches(punches).orEmpty()
                if (
                    row.day > 1 &&
                        (entries.firstOrNull()?.kind == PunchKind.EXIT || continued.isNotEmpty())
                ) {
                    Text(
                        stringResource(R.string.payroll_work_day_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    entries
                        .filter { it.kind == PunchKind.ENTRY }
                        .distinctBy { it.minute }
                        .forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = entry.minute in continued,
                                    onCheckedChange = { checked ->
                                        continued =
                                            if (checked) (continued + entry.minute).distinct()
                                            else continued - entry.minute
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.testTag("payroll_continue_${entry.minute}"),
                                )
                                Text(
                                    stringResource(
                                        R.string.payroll_continue_entry,
                                        payrollClock(entry.minute),
                                    ),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                }
                if (row.daylightSaving)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = dst, onCheckedChange = { dst = it }, enabled = !busy)
                        Text(
                            stringResource(R.string.payroll_dst_check),
                            modifier = Modifier.weight(1f),
                        )
                    }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        enabled = !busy,
                        modifier = Modifier.testTag("payroll_row_checked"),
                    )
                    Text(
                        stringResource(R.string.payroll_review_check),
                        modifier = Modifier.weight(1f),
                    )
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        amount,
                        punches,
                        dst,
                        continued
                            .filter { minute ->
                                PayrollParser.punches(punches).orEmpty().any {
                                    it.kind == PunchKind.ENTRY && it.minute == minute
                                }
                            }
                            .toSet(),
                    )
                },
                enabled =
                    checked &&
                        !busy &&
                        PayrollParser.duration(amount, 1440) != null &&
                        PayrollParser.punches(punches) != null,
                modifier = Modifier.testTag("payroll_save_row"),
            ) {
                Text(stringResource(R.string.payroll_save_row))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.payroll_cancel))
            }
        },
    )
}

@Composable
private fun PayrollSourceDialog(bitmap: Bitmap?, onDismiss: () -> Unit) {
    var zoom by rememberSaveable { mutableFloatStateOf(2f) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.payroll_source),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (bitmap == null) CircularProgressIndicator()
                else
                    BoxWithConstraints(
                        Modifier.fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val width = maxWidth * zoom
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            Image(
                                bitmap.asImageBitmap(),
                                stringResource(R.string.payroll_source),
                                Modifier.width(width)
                                    .aspectRatio(bitmap.width.toFloat() / bitmap.height),
                            )
                        }
                    }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { zoom = (zoom - 1f).coerceAtLeast(1f) },
                        enabled = zoom > 1f,
                    ) {
                        Text(stringResource(R.string.payroll_zoom_out))
                    }
                    OutlinedButton(
                        onClick = { zoom = (zoom + 1f).coerceAtMost(5f) },
                        enabled = zoom < 5f,
                    ) {
                        Text(stringResource(R.string.payroll_zoom_in))
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("payroll_close_source"),
                    ) {
                        Text(stringResource(R.string.payroll_close))
                    }
                }
            }
        }
    }
}
