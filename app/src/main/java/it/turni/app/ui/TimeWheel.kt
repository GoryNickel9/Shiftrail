package it.turni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import it.turni.app.R
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun TimeField(
    value: String,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedCard(
        onClick = {
            focus.clearFocus()
            keyboard?.hide()
            onClick()
        },
        modifier = modifier.testTag(tag),
        enabled = enabled,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                value.ifBlank { "--:--" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Compose-only wheels avoid native view remeasurement inside nested edit dialogs. */
@Composable
fun NumberWheel(
    value: Int,
    values: List<Int>,
    label: String,
    tag: String,
    onChange: (Int) -> Unit,
) {
    val index = values.indexOf(value).coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChange)
    LaunchedEffect(list, values) {
        snapshotFlow {
            if (list.isScrollInProgress) null
            else {
                val layout = list.layoutInfo
                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
            }
        }
            .distinctUntilChanged()
            .collect { selected -> selected?.let { changed(values[it]) } }
    }
    fun select(requested: Int) {
        val selected = requested.coerceIn(values.indices)
        scope.launch {
            list.scrollToItem(selected)
            changed(values[selected])
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.width(104.dp).height(150.dp)) {
            Box(
                Modifier.fillMaxWidth()
                    .height(50.dp)
                    .align(Alignment.Center)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.shapes.small,
                    )
            )
            LazyColumn(
                state = list,
                flingBehavior = rememberSnapFlingBehavior(list),
                contentPadding = PaddingValues(vertical = 50.dp),
                modifier =
                    Modifier.fillMaxSize()
                        .testTag(tag)
                        .semantics {
                            contentDescription = label
                            progressBarRangeInfo =
                                ProgressBarRangeInfo(
                                    index.toFloat(),
                                    0f..values.lastIndex.toFloat(),
                                    (values.size - 2).coerceAtLeast(0),
                                )
                            setProgress { requested ->
                                select(requested.toInt())
                                true
                            }
                        }
                        .onPreviewKeyEvent {
                            if (
                                it.type == KeyEventType.KeyDown &&
                                    (it.key == Key.DirectionDown || it.key == Key.DirectionUp)
                            ) {
                                select(index + if (it.key == Key.DirectionDown) 1 else -1)
                                true
                            } else false
                        }
                        .focusable(),
            ) {
                itemsIndexed(values) { position, number ->
                    Box(
                        Modifier.fillMaxWidth()
                            .height(50.dp)
                            .clickable { select(position) }
                            .clearAndSetSemantics {},
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "%02d".format(number),
                            style = MaterialTheme.typography.headlineSmall,
                            color =
                                if (number == value) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeWheelDialog(
    value: LocalTime,
    onDismiss: () -> Unit,
    minuteStep: Int = 15,
    onChoose: (LocalTime) -> Unit,
) {
    var hour by rememberSaveable { mutableIntStateOf(value.hour) }
    // Confirming is explicit: opening/cancelling never rounds an existing calendar time.
    var minute by rememberSaveable { mutableIntStateOf(value.minute / minuteStep * minuteStep) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_time)) },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberWheel(
                    hour,
                    (0..23).toList(),
                    stringResource(R.string.time_hours),
                    "time_hour",
                ) {
                    hour = it
                }
                Text(":", style = MaterialTheme.typography.headlineMedium)
                NumberWheel(
                    minute,
                    (0..59 step minuteStep).toList(),
                    stringResource(R.string.time_minutes),
                    "time_minute",
                ) {
                    minute = it
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onChoose(LocalTime.of(hour, minute)) },
                Modifier.testTag("confirm_time"),
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_time")) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
