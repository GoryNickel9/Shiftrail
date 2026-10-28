package it.turni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.turni.app.R
import it.turni.app.data.UnpaidBreakEntity
import it.turni.core.*
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnpaidBreakDialog(
    period: WorkPeriod,
    pauses: List<UnpaidBreakEntity>,
    previousId: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (UnpaidBreak) -> Unit,
) {
    val previous = pauses.firstOrNull { it.id == previousId }?.domain()
    val suggested =
        period.paidIntervals().firstOrNull {
            Duration.between(it.first, it.second).toMinutes() >= 5
        }
    val initial =
        previous?.start
            ?: (suggested?.first ?: period.startDateTime).let {
                it.withSecond(0).withNano(0).plusMinutes(((5 - it.minute % 5) % 5).toLong())
            }
    val initialEnd =
        previous?.end
            ?: minOf(initial.plusMinutes(30), suggested?.second ?: period.endDateTime).let {
                it.withMinute(it.minute / 5 * 5).withSecond(0).withNano(0)
            }
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate().toString()) }
    var start by rememberSaveable { mutableStateOf(initial.toLocalTime().toString()) }
    var end by rememberSaveable { mutableStateOf(initialEnd.toLocalTime().toString()) }
    var choosing by rememberSaveable { mutableStateOf<String?>(null) }
    val longPeriod = period.endDateTime >= period.startDateTime.plusDays(1)
    val candidate = runCatching {
        val startTime = LocalTime.parse(start)
        val endTime = LocalTime.parse(end)
        val day =
            if (longPeriod) LocalDate.parse(date)
            else period.date.plusDays(if (startTime < period.start) 1 else 0)
        UnpaidBreak(
            day.atTime(startTime),
            day.plusDays(if (endTime <= startTime) 1 else 0).atTime(endTime),
        )
    }
        .getOrNull()
    val problem = runCatching {
        requireNotNull(candidate) { "BREAK_INVALID" }
        period
            .copy(breaks = pauses.filter { it.id != previousId }.map { it.domain() } + candidate)
            .validateBreaks()
    }
        .exceptionOrNull()
        ?.message
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp).testTag("break_editor")
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(
                        if (previousId == null) R.string.add_break else R.string.edit_break
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "${period.date.format(DateTimeFormatter.ofPattern("dd/MM"))} · ${period.start} → ${period.end}"
                )
                if (longPeriod)
                    OutlinedTextField(
                        date,
                        { date = it },
                        Modifier.fillMaxWidth().testTag("break_date"),
                        singleLine = true,
                        label = { Text(stringResource(R.string.date)) },
                    )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField(
                        start,
                        stringResource(R.string.break_start),
                        "break_start",
                        Modifier.weight(1f),
                        enabled = !busy,
                    ) {
                        choosing = "start"
                    }
                    TimeField(
                        end,
                        stringResource(R.string.break_end),
                        "break_end",
                        Modifier.weight(1f),
                        enabled = !busy,
                    ) {
                        choosing = "end"
                    }
                }
                candidate?.let {
                    Text(
                        "${it.start.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))} → ${it.end.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))}"
                    )
                    val minutes = Duration.between(it.start, it.end).toMinutes()
                    Text(stringResource(R.string.hours_duration, minutes / 60, minutes % 60))
                }
                Text(
                    stringResource(R.string.break_calendar_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (problem != null)
                    Text(
                        stringResource(
                            when (problem) {
                                "BREAK_OUTSIDE_SHIFT" -> R.string.break_outside
                                "BREAK_OVERLAP" -> R.string.break_overlap
                                else -> R.string.break_invalid
                            }
                        ),
                        Modifier.testTag("break_error"),
                        color = MaterialTheme.colorScheme.error,
                    )
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.testTag("cancel_break"),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { candidate?.let(onSave) },
                    enabled = !busy && problem == null,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("save_break"),
                ) {
                    Text(stringResource(R.string.save_break))
                }
            }
        }
    }
    choosing?.let { field ->
        TimeWheelDialog(
            value = LocalTime.parse(if (field == "start") start else end),
            onDismiss = { choosing = null },
            minuteStep = 5,
        ) {
            if (field == "start") start = it.toString() else end = it.toString()
            choosing = null
        }
    }
}
