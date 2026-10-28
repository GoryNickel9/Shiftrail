package it.turni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.turni.app.R
import it.turni.core.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPicker(
    month: YearMonth,
    selected: Int,
    periods: List<WorkPeriod>,
    onDismiss: () -> Unit,
    onChoose: (Int) -> Unit,
) {
    var draft by rememberSaveable(month.toString()) { mutableIntStateOf(selected) }
    val weeks = WorkingHours.monthWeeks(month)
    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
    val window = weeks.getOrNull(draft) ?: HoursWindow(month.atDay(1), month.atEndOfMonth())
    val total = WorkingHours.summarize(periods, window).totalMinutes
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        modifier = Modifier.testTag("week_picker"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.choose_period),
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
            )
            IconButton(onClick = onDismiss, Modifier.testTag("close_week_picker")) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close))
            }
        }
        LazyColumn(
            Modifier.weight(1f, fill = false).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ITALIAN)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                OutlinedCard(
                    onClick = { draft = -1 },
                    modifier =
                        Modifier.fillMaxWidth().testTag("week_option_month").semantics {
                            this.selected = draft == -1
                        },
                    colors =
                        CardDefaults.outlinedCardColors(
                            containerColor =
                                if (draft == -1) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                        ),
                ) {
                    Text(
                        stringResource(R.string.hours_month),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth()) {
                    listOf("L", "M", "M", "G", "V", "S", "D").forEach {
                        Text(
                            it,
                            Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            itemsIndexed(weeks) { index, week ->
                OutlinedCard(
                    onClick = { draft = index },
                    modifier =
                        Modifier.fillMaxWidth().testTag("week_option_$index").semantics {
                            this.selected = draft == index
                        },
                    colors =
                        CardDefaults.outlinedCardColors(
                            containerColor =
                                if (draft == index) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                        ),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${week.from.format(formatter)} – ${week.until.format(formatter)}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (LocalDate.now() in week.from..week.until)
                                Text(
                                    stringResource(R.string.this_week),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                        }
                        Row(Modifier.fillMaxWidth()) {
                            val monday = week.from.with(DayOfWeek.MONDAY)
                            repeat(7) { offset ->
                                val date = monday.plusDays(offset.toLong())
                                val inMonth = YearMonth.from(date) == month
                                Box(
                                    Modifier.weight(1f)
                                        .background(
                                            if (date == LocalDate.now())
                                                MaterialTheme.colorScheme.secondaryContainer
                                            else androidx.compose.ui.graphics.Color.Transparent,
                                            RoundedCornerShape(6.dp),
                                        )
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color =
                                            MaterialTheme.colorScheme.onSurface.copy(
                                                alpha = if (inMonth) 1f else 0.4f
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.period_hours_preview, total / 60, total % 60),
            Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("week_hours_preview"),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        ActionFooter(
            stringResource(if (draft < 0) R.string.show_month else R.string.show_week),
            action = { onChoose(draft) },
        )
    }
}
