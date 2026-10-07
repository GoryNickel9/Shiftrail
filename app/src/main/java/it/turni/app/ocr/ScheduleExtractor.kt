package it.turni.app.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import it.turni.app.imageprocessing.ImageProcessor
import it.turni.core.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ImageInput(
    val bitmap: Bitmap,
    val profile: EmployeeProfile,
    val referenceDate: LocalDate,
)

data class ExtractedSchedule(
    val tokens: List<OcrToken>,
    val table: ExtractionResult,
    val enhanced: Boolean,
    val elapsedMillis: Long,
    val tiled: Boolean = false,
)

interface ScheduleExtractor {
    suspend fun extract(image: ImageInput): ExtractedSchedule
}

class LocalScheduleExtractor(private val images: ImageProcessor) : ScheduleExtractor {
    internal var onReading: ((String, ExtractionResult) -> Unit)? = null

    private suspend fun recognize(bitmap: Bitmap, recognizer: TextRecognizer): List<OcrToken> {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        return result.textBlocks
            .flatMap { it.lines }
            .flatMap { it.elements }
            .mapNotNull { element ->
                val rect = element.boundingBox ?: return@mapNotNull null
                OcrToken(
                    element.text,
                    Box(
                        rect.left.toFloat() / bitmap.width,
                        rect.top.toFloat() / bitmap.height,
                        rect.right.toFloat() / bitmap.width,
                        rect.bottom.toFloat() / bitmap.height,
                    ),
                    element.confidence.takeIf { it >= 0f },
                )
            }
    }

    private suspend fun recognizeRegion(
        bitmap: Bitmap,
        region: Box,
        scale: Float,
        recognizer: TextRecognizer,
    ): List<OcrToken> {
        val x = (region.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y = (region.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (region.right * bitmap.width).toInt().coerceIn(x + 1, bitmap.width)
        val bottom = (region.bottom * bitmap.height).toInt().coerceIn(y + 1, bitmap.height)
        val width = right - x
        val height = bottom - y
        val padding = 24
        val crop =
            Bitmap.createBitmap(
                (width * scale).toInt() + padding * 2,
                (height * scale).toInt() + padding * 2,
                Bitmap.Config.ARGB_8888,
            )
        Canvas(crop).apply {
            drawColor(Color.WHITE)
            drawBitmap(
                bitmap,
                android.graphics.Rect(x, y, right, bottom),
                android.graphics.RectF(
                    padding.toFloat(),
                    padding.toFloat(),
                    padding + width * scale,
                    padding + height * scale,
                ),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
        try {
            return recognize(crop, recognizer).mapNotNull { token ->
                val b = token.box
                val left = (b.left * crop.width - padding) / scale
                val top = (b.top * crop.height - padding) / scale
                val localRight = (b.right * crop.width - padding) / scale
                val localBottom = (b.bottom * crop.height - padding) / scale
                if (left < 1 || top < 1 || localRight > width - 1 || localBottom > height - 1) null
                else
                    token.copy(
                        box =
                            OcrGeometry.map(
                                Box(
                                    left / width,
                                    top / height,
                                    localRight / width,
                                    localBottom / height,
                                ),
                                Box(
                                    x.toFloat() / bitmap.width,
                                    y.toFloat() / bitmap.height,
                                    right.toFloat() / bitmap.width,
                                    bottom.toFloat() / bitmap.height,
                                ),
                            )
                    )
            }
        } finally {
            crop.recycle()
        }
    }

    private suspend fun recognizeTiles(bitmap: Bitmap, recognizer: TextRecognizer): List<OcrToken> {
        val width = minOf(bitmap.width, maxOf(640, bitmap.width / 3))
        val height = minOf(bitmap.height, maxOf(160, width / 3, bitmap.height / 8))
        fun origins(length: Int, size: Int): List<Int> =
            (0..maxOf(0, length - size) step maxOf(1, size * 3 / 4))
                .toList()
                .plus(maxOf(0, length - size))
                .distinct()
        val tokens = mutableListOf<OcrToken>()
        for (y in origins(bitmap.height, height)) {
            for (x in origins(bitmap.width, width)) {
                val scale = (1920f / width).coerceIn(1f, 3f)
                tokens +=
                    recognizeRegion(
                        bitmap,
                        Box(
                            x.toFloat() / bitmap.width,
                            y.toFloat() / bitmap.height,
                            (x + width).toFloat() / bitmap.width,
                            (y + height).toFloat() / bitmap.height,
                        ),
                        scale,
                        recognizer,
                    )
            }
        }
        return OcrGeometry.merge(tokens)
    }

    private suspend fun refineHeader(
        bitmap: Bitmap,
        tokens: List<OcrToken>,
        table: ExtractionResult,
        recognizer: TextRecognizer,
    ): List<OcrToken> {
        val headers =
            table.columns.mapNotNull { column ->
                tokens.firstOrNull {
                    ItalianDateParser.weekday(it.text) == column.weekday &&
                        kotlin.math.abs(it.centerX - column.center) < 0.001f
                }
            }
        if (headers.size < 2) return tokens
        val height = headers.map { it.box.height }.sorted()[headers.size / 2]
        val top = (headers.minOf { it.box.top } - height * 0.5f).coerceAtLeast(0f)
        val bottom = (headers.maxOf { it.box.bottom } + height * 2f).coerceAtMost(1f)
        val width = minOf(bitmap.width, maxOf(640, bitmap.width / 3))
        val starts =
            (0..maxOf(0, bitmap.width - width) step maxOf(1, width * 3 / 4))
                .toList()
                .plus(maxOf(0, bitmap.width - width))
                .distinct()
        val local = starts.flatMap { x ->
            recognizeRegion(
                bitmap,
                Box(
                    x.toFloat() / bitmap.width,
                    top,
                    (x + width).toFloat() / bitmap.width,
                    bottom,
                ),
                (1920f / width).coerceIn(1f, 3f),
                recognizer,
            )
        }
        return OcrGeometry.merge(
            tokens.filterNot { it.centerY >= top && it.centerY <= bottom } + local
        )
    }

    override suspend fun extract(image: ImageInput): ExtractedSchedule =
        withContext(Dispatchers.Default) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val start = android.os.SystemClock.elapsedRealtime()
                val original = recognize(image.bitmap, recognizer)
                val enhancedBitmap = images.contrast(image.bitmap)
                val enhanced =
                    try {
                        recognize(enhancedBitmap, recognizer)
                    } finally {
                        enhancedBitmap.recycle()
                    }
                val parser = TableParser()
                val a = parser.extract(original, image.profile, image.referenceDate)
                val b = parser.extract(enhanced, image.profile, image.referenceDate)
                onReading?.invoke("original", a)
                onReading?.invoke("contrast", b)
                fun quality(table: ExtractionResult) =
                    table.cells.count {
                        it.type == CellType.WORK_SHIFT || it.type == CellType.CODE
                    } * 0.5 +
                        table.columns.size * 4.0 +
                        (table.selected?.score ?: 0.0) * 2.0 +
                        table.columns.count { it.date != null } * 0.5 -
                        table.issues.count { it != ExtractionIssue.DATE_CONFIRMATION } * 2
                val useEnhanced = quality(b) > quality(a)
                var chosenTokens = if (useEnhanced) enhanced else original
                var chosenTable = if (useEnhanced) b else a
                var tiled = false
                if (
                    chosenTable.selected == null ||
                        chosenTable.columns.size < 7 ||
                        chosenTable.cells.any {
                            it.type == CellType.UNKNOWN || it.ocrOriginal != null
                        }
                ) {
                    var denseTokens = recognizeTiles(image.bitmap, recognizer)
                    var denseTable = parser.extract(denseTokens, image.profile, image.referenceDate)
                    onReading?.invoke("tiles", denseTable)
                    if (
                        denseTable.columns.size in 2..6 ||
                            denseTable.issues.any {
                                it == ExtractionIssue.INVALID_DATES
                            }
                    ) {
                        val headerTokens =
                            refineHeader(image.bitmap, denseTokens, denseTable, recognizer)
                        val headerTable =
                            parser.extract(headerTokens, image.profile, image.referenceDate)
                        if (quality(headerTable) >= quality(denseTable)) {
                            denseTokens = headerTokens
                            denseTable = headerTable
                        }
                    }
                    if (quality(denseTable) > quality(chosenTable)) {
                        chosenTokens = denseTokens
                        chosenTable = denseTable
                        tiled = true
                    }
                }
                // Retry uncertain cells in isolation after locating the employee row.
                for (cell in
                    chosenTable.cells.filter {
                        tiled || it.type == CellType.UNKNOWN || it.ocrOriginal != null
                    }) {
                    val region = cell.source ?: continue
                    val plan =
                        CellOcrPlanner.plan(
                            chosenTokens,
                            cell,
                            image.bitmap.width,
                            image.bitmap.height,
                        ) ?: continue
                    var local =
                        recognizeRegion(
                            image.bitmap,
                            plan.region,
                            plan.scale,
                            recognizer,
                        )
                    fun replaceCell(readings: List<OcrToken>) =
                        chosenTokens.filterNot {
                            it.centerX >= region.left &&
                                it.centerX < region.right &&
                                region.containsY(it.centerY)
                        } + readings
                    var combined = replaceCell(local)
                    var refined =
                        parser.extract(
                            combined,
                            image.profile,
                            image.referenceDate,
                            selectedRow = chosenTable.selected,
                        )
                    onReading?.invoke("cell", refined)
                    if (
                        (cell.type == CellType.UNKNOWN || cell.ocrOriginal != null) &&
                            refined.cells.firstOrNull { it.date == cell.date }?.type ==
                                CellType.UNKNOWN
                    ) {
                        val fallback =
                            recognizeRegion(
                                image.bitmap,
                                region,
                                (96f / (region.height * image.bitmap.height)).coerceIn(1f, 4f),
                                recognizer,
                            )
                        val candidate = replaceCell(fallback)
                        val fallbackTable =
                            parser.extract(
                                candidate,
                                image.profile,
                                image.referenceDate,
                                selectedRow = chosenTable.selected,
                            )
                        onReading?.invoke("cell-context", fallbackTable)
                        if (
                            fallback.isNotEmpty() &&
                                quality(fallbackTable) >= quality(refined) &&
                                OcrGeometry.preservesOtherCells(
                                    chosenTable,
                                    fallbackTable,
                                    cell.date,
                                )
                        ) {
                            local = fallback
                            combined = candidate
                            refined = fallbackTable
                        }
                    }
                    if (
                        quality(refined) >= quality(chosenTable) &&
                            local.isNotEmpty() &&
                            OcrGeometry.preservesOtherCells(chosenTable, refined, cell.date)
                    ) {
                        val retry = refined.cells.firstOrNull { it.date == cell.date }
                        val disagreement =
                            retry?.let { OcrGeometry.conflictingReadings(cell, it) }.orEmpty()
                        if (disagreement.isNotEmpty()) {
                            chosenTokens =
                                combined.filterNot {
                                    it.centerX >= region.left &&
                                        it.centerX < region.right &&
                                        region.containsY(it.centerY)
                                } + OcrToken(disagreement.joinToString(" / "), region, 0.5f)
                            chosenTable =
                                parser.extract(
                                    chosenTokens,
                                    image.profile,
                                    image.referenceDate,
                                    selectedRow = chosenTable.selected,
                                )
                        } else {
                            chosenTokens = combined
                            chosenTable = refined
                        }
                    }
                }
                // Preserve disagreements from the two full-image passes as well.
                val selected = chosenTable.selected
                if (selected != null) {
                    for (cell in chosenTable.cells) {
                        val region = cell.source ?: continue
                        val alternatives =
                            listOf(a, b)
                                .filter { pass ->
                                    val row = pass.selected
                                    row != null &&
                                        row.label == selected.label &&
                                        kotlin.math.abs(row.box.centerY - selected.box.centerY) <
                                            maxOf(row.box.height, selected.box.height)
                                }
                                .mapNotNull { pass ->
                                    pass.cells.firstOrNull { it.date == cell.date }
                                }
                                .fold(emptyList<String>()) { readings, other ->
                                    (readings + OcrGeometry.conflictingReadings(cell, other))
                                        .distinct()
                                }
                        if (alternatives.isNotEmpty()) {
                            chosenTokens =
                                chosenTokens.filterNot {
                                    it.centerX >= region.left &&
                                        it.centerX < region.right &&
                                        region.containsY(it.centerY)
                                } + OcrToken(alternatives.joinToString(" / "), region, 0.5f)
                        }
                    }
                    chosenTable =
                        parser.extract(
                            chosenTokens,
                            image.profile,
                            image.referenceDate,
                            selectedRow = selected,
                        )
                }
                ExtractedSchedule(
                    chosenTokens,
                    chosenTable,
                    useEnhanced && !tiled,
                    android.os.SystemClock.elapsedRealtime() - start,
                    tiled,
                )
            } finally {
                recognizer.close()
            }
        }
}
