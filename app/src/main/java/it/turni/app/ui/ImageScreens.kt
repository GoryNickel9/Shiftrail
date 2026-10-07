package it.turni.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.turni.app.R
import it.turni.app.imageprocessing.Corner
import it.turni.core.Box
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CropScreen(bitmap: Bitmap?, onAnalyze: (List<Corner>) -> Unit, onError: (String) -> Unit) {
    if (bitmap == null) {
        Text(stringResource(R.string.source_not_saved))
        return
    }
    var corners by
        remember(bitmap) {
            mutableStateOf(listOf(Corner(0f, 0f), Corner(1f, 0f), Corner(1f, 1f), Corner(0f, 1f)))
        }
    var active by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveImage =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) {
            uri ->
            if (uri != null)
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri).use {
                                requireNotNull(it)
                                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it))
                            }
                        }
                    } catch (_: Exception) {
                        onError(context.getString(R.string.operation_failed))
                    }
                }
        }
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.crop), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.crop_help))
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val aspect = bitmap.width.toFloat() / bitmap.height
            val imageModifier =
                if (maxWidth / maxHeight > aspect) Modifier.fillMaxHeight().aspectRatio(aspect)
                else Modifier.fillMaxWidth().aspectRatio(aspect)
            Box(imageModifier) {
                Image(bitmap.asImageBitmap(), stringResource(R.string.crop), Modifier.fillMaxSize())
                Canvas(
                    Modifier.fillMaxSize().pointerInput(bitmap) {
                        detectDragGestures(
                            onDragStart = { point ->
                                active =
                                    corners.indices.minBy { i ->
                                        hypot(
                                            corners[i].x * size.width - point.x,
                                            corners[i].y * size.height - point.y,
                                        )
                                    }
                            }
                        ) { change, delta ->
                            change.consume()
                            corners = corners.mapIndexed { index, corner ->
                                if (index == active)
                                    Corner(
                                        (corner.x + delta.x / size.width).coerceIn(0f, 1f),
                                        (corner.y + delta.y / size.height).coerceIn(0f, 1f),
                                    )
                                else corner
                            }
                        }
                    }
                ) {
                    val points = corners.map { Offset(it.x * size.width, it.y * size.height) }
                    val path =
                        Path().apply {
                            moveTo(points[0].x, points[0].y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        }
                    drawPath(path, Color(0xFF00BFA5), style = Stroke(4f))
                    points.forEach {
                        drawCircle(Color(0xFF006C67), 16f, it)
                        drawCircle(Color.White, 8f, it)
                    }
                }
            }
        }
        Button(onClick = { onAnalyze(corners) }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.analyze))
        }
        TextButton(onClick = { saveImage.launch("turni-foto.jpg") }) {
            Text(stringResource(R.string.save_photo))
        }
    }
}

@Composable
fun SourceImage(bitmap: Bitmap, source: Box, dismiss: () -> Unit) {
    val crop =
        remember(bitmap, source) {
            val x = (source.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val y = (source.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val width =
                ((source.right - source.left) * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
            val height =
                ((source.bottom - source.top) * bitmap.height)
                    .toInt()
                    .coerceIn(1, bitmap.height - y)
            Bitmap.createBitmap(bitmap, x, y, width, height)
        }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.origin)) },
        text = {
            Image(
                crop.asImageBitmap(),
                stringResource(R.string.origin),
                Modifier.fillMaxWidth().aspectRatio(crop.width.toFloat() / crop.height),
            )
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.back)) } },
    )
}

@Composable
fun DebugScreen(state: AppState) {
    val bitmap = state.analyzed ?: return
    val extraction = state.extraction ?: return
    Column(
        Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.debug), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.debug_help))
        Text(
            stringResource(
                R.string.debug_stats,
                extraction.tokens.size,
                extraction.elapsedMillis,
                extraction.enhanced.toString(),
            )
        )
        Text(stringResource(R.string.debug_tiled, extraction.tiled.toString()))
        Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
            Image(bitmap.asImageBitmap(), stringResource(R.string.debug), Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                extraction.tokens.forEach { token ->
                    drawRect(
                        Color.Blue,
                        Offset(token.box.left * size.width, token.box.top * size.height),
                        androidx.compose.ui.geometry.Size(
                            (token.box.right - token.box.left) * size.width,
                            token.box.height * size.height,
                        ),
                        style = Stroke(1.5f),
                    )
                }
                extraction.table.cells
                    .mapNotNull { it.source }
                    .ifEmpty { listOfNotNull(extraction.table.rowBand) }
                    .forEach { box ->
                        drawRect(
                            Color.Green.copy(alpha = 0.25f),
                            Offset(box.left * size.width, box.top * size.height),
                            androidx.compose.ui.geometry.Size(
                                (box.right - box.left) * size.width,
                                box.height * size.height,
                            ),
                        )
                    }
                extraction.table.columns.forEach { column ->
                    drawLine(
                        Color.Red,
                        Offset(column.left * size.width, 0f),
                        Offset(column.left * size.width, size.height),
                        2f,
                    )
                }
            }
        }
        extraction.table.selected?.let { Text(it.label) }
        extraction.table.cells.forEach { cell ->
            Text("${cell.date}: ${cell.raw} (${(cell.confidence * 100).toInt()}%)")
        }
    }
}
