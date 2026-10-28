package it.turni.app.ui

import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import it.turni.app.R
import java.time.LocalTime

/** Native scrolling wheels with the keyboard disabled, including on accessibility focus. */
@Composable
fun NumberWheel(
    value: Int,
    values: List<Int>,
    label: String,
    tag: String,
    onChange: (Int) -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val index = values.indexOf(value).coerceAtLeast(0)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        AndroidView(
            modifier =
                Modifier.width(104.dp).height(150.dp).testTag(tag).semantics {
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            index.toFloat(),
                            0f..values.lastIndex.toFloat(),
                            (values.size - 2).coerceAtLeast(0),
                        )
                    setProgress { requested ->
                        onChange(values[requested.toInt().coerceIn(values.indices)])
                        true
                    }
                },
            factory = { context ->
                NumberPicker(
                        android.view.ContextThemeWrapper(
                            context,
                            if (
                                androidx.core.graphics.ColorUtils.calculateLuminance(textColor) >
                                    0.5
                            )
                                android.R.style.Theme_Material_NoActionBar
                            else android.R.style.Theme_Material_Light_NoActionBar,
                        )
                    )
                    .apply {
                        minValue = 0
                        maxValue = values.lastIndex
                        displayedValues = values.map { "%02d".format(it) }.toTypedArray()
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        wrapSelectorWheel = true
                        contentDescription = label
                    }
            },
            update = { picker ->
                picker.setOnValueChangedListener(null)
                picker.value = index
                if (Build.VERSION.SDK_INT >= 29) {
                    picker.textColor = textColor
                    picker.textSize =
                        android.util.TypedValue.applyDimension(
                            android.util.TypedValue.COMPLEX_UNIT_SP,
                            24f,
                            picker.resources.displayMetrics,
                        )
                    picker.selectionDividerHeight = 1
                } else {
                    // Android 8/9 use the native theme text colors for this widget.
                    picker.setBackgroundColor(AndroidColor.TRANSPARENT)
                }
                picker.setOnValueChangedListener { _, _, selected -> onChange(values[selected]) }
            },
        )
    }
}

@Composable
fun TimeWheelDialog(value: LocalTime, onDismiss: () -> Unit, onChoose: (LocalTime) -> Unit) {
    var hour by rememberSaveable { mutableIntStateOf(value.hour) }
    // Confirming is explicit: opening/cancelling never rounds an existing calendar time.
    var minute by rememberSaveable { mutableIntStateOf(value.minute / 15 * 15) }
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
                    listOf(0, 15, 30, 45),
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
