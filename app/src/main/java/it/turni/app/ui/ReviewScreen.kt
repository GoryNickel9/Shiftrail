package it.turni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.turni.app.R
import it.turni.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReviewScreen(state: AppState, vm: AppViewModel, onCalendar: () -> Unit, onDebug: () -> Unit) {
    val extraction = state.extraction ?: return
    val result = extraction.table
    var editing by remember { mutableIntStateOf(-2) }
    var source by remember { mutableStateOf<Box?>(null) }
    var monday by rememberSaveable {
        mutableStateOf(
            (result.cells.firstOrNull()?.date ?: LocalDate.now())
                .with(java.time.DayOfWeek.MONDAY)
                .toString()
        )
    }
    val issueResources =
        mapOf(
            ExtractionIssue.EMPTY_OCR to R.string.empty_ocr,
            ExtractionIssue.NAME_NOT_FOUND to R.string.name_not_found,
            ExtractionIssue.AMBIGUOUS_NAME to R.string.ambiguous_name,
            ExtractionIssue.HEADER_NOT_FOUND to R.string.header_not_found,
            ExtractionIssue.DATE_CONFIRMATION to R.string.date_confirmation,
            ExtractionIssue.INVALID_DATES to R.string.invalid_dates,
        )
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.review), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.review_help))
        }
        if (state.duplicatePhoto)
            item {
                Text(
                    stringResource(R.string.duplicate_photo),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        result.issues.forEach { issue ->
            item {
                Text(
                    stringResource(issueResources.getValue(issue)),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (result.selected == null && result.columns.isNotEmpty()) {
            item { Text(stringResource(R.string.select_row)) }
            val candidates =
                result.candidates.ifEmpty {
                    TableParser().selectableRows(extraction.tokens, result.columns)
                }
            candidates.forEach { candidate ->
                item {
                    OutlinedButton(onClick = { vm.chooseRow(candidate) }, Modifier.fillMaxWidth()) {
                        Text(candidate.label)
                    }
                }
            }
        }
        if (
            ExtractionIssue.DATE_CONFIRMATION in result.issues ||
                ExtractionIssue.INVALID_DATES in result.issues
        )
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        monday,
                        { monday = it },
                        label = { Text(stringResource(R.string.monday)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = { vm.confirmMonday(monday) }) {
                        Text(stringResource(R.string.confirm_dates))
                    }
                }
            }
        itemsIndexed(result.cells, key = { _, cell -> cell.date.toString() }) { index, cell ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        cell.date.format(
                            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when (cell.type) {
                            CellType.WORK_SHIFT -> "${cell.start} → ${cell.end}"
                            CellType.CODE -> cell.code.orEmpty()
                            CellType.EMPTY -> stringResource(R.string.empty_cell)
                            CellType.UNKNOWN ->
                                "${stringResource(R.string.unknown_cell)}: ${cell.raw}"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (cell.endNextDay) Text(stringResource(R.string.overnight))
                    cell.ocrOriginal?.let { original ->
                        Text(
                            stringResource(R.string.ocr_original_reading, original),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (cell.type == CellType.UNKNOWN && cell.raw.contains(" / "))
                        Text(
                            stringResource(R.string.ocr_disagreement),
                            color = MaterialTheme.colorScheme.error,
                        )
                    if (cell.type == CellType.CODE && state.mappings.none { it.code == cell.code })
                        Text(
                            stringResource(R.string.unmapped_code),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    Text(
                        stringResource(R.string.confidence_label, (cell.confidence * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!cell.reviewed && cell.confidence < 0.9)
                        Text(
                            stringResource(
                                if (cell.needsReview) R.string.low_confidence
                                else R.string.check_ocr
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    if (cell.needsReview && !cell.reviewed && cell.type != CellType.UNKNOWN)
                        TextButton(onClick = { vm.acknowledge(index) }) {
                            Text(stringResource(R.string.acknowledge))
                        }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editing = index }) {
                            Text(stringResource(R.string.edit))
                        }
                        TextButton(onClick = { vm.removeCell(index) }) {
                            Text(stringResource(R.string.delete))
                        }
                        if (cell.source != null)
                            TextButton(
                                onClick = {
                                    val bitmap = state.analyzed
                                    source =
                                        if (bitmap != null) {
                                            CellOcrPlanner.plan(
                                                    extraction.tokens,
                                                    cell,
                                                    bitmap.width,
                                                    bitmap.height,
                                                )
                                                ?.region ?: cell.source
                                        } else cell.source
                                }
                            ) {
                                Text(stringResource(R.string.origin))
                            }
                    }
                }
            }
        }
        if (result.selected != null)
            item {
                OutlinedButton(onClick = { editing = -1 }) { Text(stringResource(R.string.add)) }
            }
        if (state.settings.developer)
            item { TextButton(onClick = onDebug) { Text(stringResource(R.string.debug)) } }
        item {
            Button(
                onClick = onCalendar,
                enabled =
                    result.selected != null &&
                        result.issues.isEmpty() &&
                        result.cells.isNotEmpty() &&
                        result.cells.all { !it.needsReview || it.reviewed },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.continue_label))
            }
        }
    }
    if (editing != -2) {
        val cell = result.cells.getOrNull(editing)
        EditCellDialog(
            cell,
            defaultDate =
                state.confirmedMonday ?: result.cells.firstOrNull()?.date ?: LocalDate.now(),
            dismiss = { editing = -2 },
        ) { date, raw ->
            if (vm.editCell(editing, date, raw)) {
                editing = -2
                true
            } else false
        }
    }
    source?.let { box -> state.analyzed?.let { SourceImage(it, box) { source = null } } }
}

@Composable
private fun EditCellDialog(
    cell: ShiftCell?,
    defaultDate: LocalDate,
    dismiss: () -> Unit,
    save: (String, String) -> Boolean,
) {
    var date by remember { mutableStateOf((cell?.date ?: defaultDate).toString()) }
    var raw by remember { mutableStateOf(cell?.raw.orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    date,
                    { date = it },
                    label = { Text(stringResource(R.string.date)) },
                )
                OutlinedTextField(
                    raw,
                    { raw = it },
                    label = { Text(stringResource(R.string.cell_content)) },
                )
                if (invalid)
                    Text(
                        stringResource(R.string.invalid_cell),
                        color = MaterialTheme.colorScheme.error,
                    )
            }
        },
        confirmButton = {
            TextButton(onClick = { invalid = !save(date, raw) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
