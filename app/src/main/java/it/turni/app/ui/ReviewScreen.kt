package it.turni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.turni.app.R
import it.turni.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReviewScreen(state: AppState, vm: AppViewModel, onCalendar: () -> Unit) {
    val extraction = state.extraction ?: return
    val result = extraction.table
    var editing by remember { mutableIntStateOf(-2) }
    var deleting by remember { mutableIntStateOf(-1) }
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
    val pending =
        result.cells.count { it.type == CellType.UNKNOWN || (it.needsReview && !it.reviewed) }
    val ready =
        result.selected != null &&
            result.issues.isEmpty() &&
            result.cells.isNotEmpty() &&
            pending == 0
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ImportProgress(2)
                Text(
                    stringResource(R.string.review),
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.headlineMedium,
                )
                result.selected?.let {
                    Text(
                        it.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    stringResource(R.string.review_help),
                    Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            stringResource(issueResources.getValue(issue)),
                            Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
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
                        OutlinedButton(
                            onClick = { vm.chooseRow(candidate) },
                            Modifier.fillMaxWidth(),
                        ) {
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
                            singleLine = true,
                        )
                        Button(onClick = { vm.confirmMonday(monday) }) {
                            Text(stringResource(R.string.confirm_dates))
                        }
                    }
                }
            itemsIndexed(result.cells, key = { _, cell -> cell.date.toString() }) { index, cell ->
                ReviewCellCard(
                    cell,
                    onEdit = { editing = index },
                    onDelete = { deleting = index },
                    onAcknowledge = { vm.acknowledge(index) },
                    onSource =
                        if (cell.source == null || state.analyzed == null) null
                        else {
                            {
                                val bitmap = state.analyzed
                                source =
                                    CellOcrPlanner.plan(
                                            extraction.tokens,
                                            cell,
                                            bitmap.width,
                                            bitmap.height,
                                        )
                                        ?.region ?: cell.source
                            }
                        },
                )
            }
            if (result.selected != null)
                item {
                    OutlinedButton(onClick = { editing = -1 }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.add))
                    }
                }
        }
        ActionFooter(
            stringResource(R.string.continue_label),
            enabled = ready && !state.busy,
            note =
                when {
                    result.selected == null -> stringResource(R.string.review_row_first)
                    result.issues.isNotEmpty() -> stringResource(R.string.review_required)
                    result.cells.isEmpty() -> stringResource(R.string.no_shifts)
                    pending > 0 ->
                        pluralStringResource(
                            R.plurals.review_count,
                            result.cells.size,
                            pending,
                            result.cells.size,
                        )
                    else -> stringResource(R.string.all_ready)
                },
            action = onCalendar,
        )
    }
    if (editing != -2) {
        EditCellDialog(
            result.cells.getOrNull(editing),
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
    if (deleting >= 0)
        AlertDialog(
            onDismissRequest = { deleting = -1 },
            title = { Text(stringResource(R.string.delete_cell_title)) },
            text = { Text(stringResource(R.string.delete_cell_note)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeCell(deleting)
                        deleting = -1
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = -1 }) { Text(stringResource(R.string.cancel)) }
            },
        )
    source?.let { box -> state.analyzed?.let { SourceImage(it, box) { source = null } } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCellCard(
    cell: ShiftCell,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAcknowledge: () -> Unit,
    onSource: (() -> Unit)?,
) {
    val pending = cell.type == CellType.UNKNOWN || (cell.needsReview && !cell.reviewed)
    var menu by remember { mutableStateOf(false) }
    var details by rememberSaveable(cell.date) { mutableStateOf(false) }
    val status =
        when {
            cell.type == CellType.UNKNOWN -> R.string.unknown_badge
            pending -> R.string.review_badge
            cell.reviewed -> R.string.verified_badge
            else -> R.string.ready_badge
        }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        cell.date.format(
                            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Surface(
                        color =
                            if (pending) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            stringResource(status),
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (pending) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Box {
                    val description = stringResource(R.string.cell_actions, cell.date.toString())
                    IconButton(
                        onClick = { menu = true },
                        Modifier.semantics { contentDescription = description },
                    ) {
                        Text("⋮", fontSize = 24.sp)
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reading_details)) },
                            onClick = {
                                menu = false
                                details = !details
                            },
                        )
                    }
                }
            }
            Text(
                when (cell.type) {
                    CellType.WORK_SHIFT -> "${cell.start} → ${cell.end}"
                    CellType.CODE -> stringResource(R.string.rest_day)
                    CellType.EMPTY -> stringResource(R.string.empty_cell)
                    CellType.UNKNOWN -> "${stringResource(R.string.unknown_cell)}: ${cell.raw}"
                },
                style =
                    if (cell.type == CellType.UNKNOWN) MaterialTheme.typography.bodyLarge
                    else MaterialTheme.typography.headlineSmall,
            )
            if (cell.endNextDay)
                Text(stringResource(R.string.overnight), style = MaterialTheme.typography.bodySmall)
            if (cell.type == CellType.CODE)
                Text(
                    stringResource(R.string.rest_day_note),
                    style = MaterialTheme.typography.bodySmall,
                )
            if (pending || details) {
                cell.ocrOriginal?.let {
                    Text(
                        stringResource(R.string.ocr_original_reading, it),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    stringResource(R.string.confidence_label, cell.readingScore),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (cell.type == CellType.UNKNOWN && cell.raw.contains(" / "))
                Text(
                    stringResource(R.string.ocr_disagreement),
                    color = MaterialTheme.colorScheme.error,
                )
            else if (pending)
                Text(
                    stringResource(R.string.low_confidence),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                onSource?.let { TextButton(onClick = it) { Text(stringResource(R.string.origin)) } }
            }
            if (pending && cell.type != CellType.UNKNOWN)
                FilledTonalButton(
                    onClick = onAcknowledge,
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.acknowledge))
                }
        }
    }
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
