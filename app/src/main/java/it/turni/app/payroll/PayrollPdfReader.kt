package it.turni.app.payroll

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import it.turni.core.*
import java.io.File
import java.time.YearMonth
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** The supported RP7000 grid is detected from rules, not from the employee's coordinates. */
class PayrollPdfReader {
    data class Grid(val rows: List<Int>, val verticals: List<Int>)

    suspend fun read(
        file: File,
        fallbackMonth: YearMonth,
        progress: (Int) -> Unit = {},
    ): PayrollReport =
        withContext(Dispatchers.Default) {
            val bitmap = render(file, 2400)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val grid = findGrid(bitmap)
                val header = recognize(bitmap, 0, grid.rows.first(), recognizer)
                val footer = recognize(bitmap, grid.rows.last(), bitmap.height, recognizer)
                val month = PayrollParser.month(header) ?: fallbackMonth
                require(month.lengthOfMonth() < grid.rows.size) { "PAYROLL_GRID" }
                val columns = grid.verticals.drop(1).take(11).map { it.toFloat() / bitmap.width }
                val days =
                    (0 until grid.rows.lastIndex).map { index ->
                        coroutineContext.ensureActive()
                        val top = grid.rows[index]
                        val bottom = grid.rows[index + 1]
                        val tokens =
                            recognize(bitmap, top + 3, bottom - 3, recognizer, grid.verticals)
                        progress(((index + 1) * 100) / grid.rows.lastIndex)
                        val source =
                            Box(
                                grid.verticals.first().toFloat() / bitmap.width,
                                top.toFloat() / bitmap.height,
                                grid.verticals.last().toFloat() / bitmap.width,
                                bottom.toFloat() / bitmap.height,
                            )
                        val initial = PayrollParser.row(index + 1, tokens, source, columns)
                        if (
                            initial.issues.any {
                                it == PayrollReadIssue.DAY || it == PayrollReadIssue.TIME
                            }
                        ) {
                            // Separate the day number from clock digits when OCR joins adjacent
                            // cells.
                            val dayTokens =
                                recognize(
                                    bitmap,
                                    top + 3,
                                    bottom - 3,
                                    recognizer,
                                    left = grid.verticals[0] + 3,
                                    right = grid.verticals[1] - 3,
                                )
                            val times =
                                recognize(
                                    bitmap,
                                    top + 3,
                                    bottom - 3,
                                    recognizer,
                                    grid.verticals,
                                    left = grid.verticals[1] + 3,
                                    right = grid.verticals[11] - 3,
                                )
                            val reread =
                                PayrollParser.row(
                                    index + 1,
                                    dayTokens +
                                        times +
                                        tokens.filter { it.centerX >= columns.last() },
                                    source,
                                    columns,
                                )
                            if (
                                PayrollReadIssue.TIME !in initial.issues &&
                                    initial.punches != reread.punches
                            )
                                reread.copy(issues = reread.issues + PayrollReadIssue.TIME)
                            else reread
                        } else initial
                    }
                PayrollReport(
                    month,
                    PayrollParser.employeeName(header),
                    PayrollParser.printedTotal(footer),
                    days,
                )
            } finally {
                recognizer.close()
                bitmap.recycle()
            }
        }

    private suspend fun recognize(
        bitmap: Bitmap,
        top: Int,
        bottom: Int,
        recognizer: TextRecognizer,
        eraseLines: List<Int> = emptyList(),
        left: Int = 0,
        right: Int = bitmap.width,
    ): List<OcrToken> {
        val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        try {
            if (eraseLines.isNotEmpty()) {
                val canvas = Canvas(crop)
                val paint = Paint().apply { color = Color.WHITE }
                eraseLines.forEach { x ->
                    canvas.drawRect(x - left - 3f, 0f, x - left + 3f, crop.height.toFloat(), paint)
                }
            }
            // Native OCR keeps the bitmap until completion, even when navigation cancels import.
            val result =
                withContext(kotlinx.coroutines.NonCancellable) {
                    recognizer.process(InputImage.fromBitmap(crop, 0)).await()
                }
            return result.textBlocks
                .flatMap { it.lines }
                .flatMap { it.elements }
                .mapNotNull { value ->
                    val rect = value.boundingBox ?: return@mapNotNull null
                    OcrToken(
                        value.text,
                        Box(
                            (left + rect.left).toFloat() / bitmap.width,
                            (top + rect.top).toFloat() / bitmap.height,
                            (left + rect.right).toFloat() / bitmap.width,
                            (top + rect.bottom).toFloat() / bitmap.height,
                        ),
                        value.confidence.takeIf { it >= 0f },
                    )
                }
        } finally {
            if (crop !== bitmap) crop.recycle()
        }
    }

    internal fun findGrid(bitmap: Bitmap): Grid {
        fun dark(pixel: Int) =
            Color.red(pixel) < 155 && Color.green(pixel) < 155 && Color.blue(pixel) < 155
        fun centers(values: List<Int>): List<Int> {
            val groups = mutableListOf<MutableList<Int>>()
            values.forEach { value ->
                if (groups.lastOrNull()?.last()?.let { value - it <= 2 } == true)
                    groups.last() += value
                else groups += mutableListOf(value)
            }
            return groups.map { it.average().roundToInt() }
        }
        val pixels = IntArray(bitmap.width)
        val horizontal = mutableListOf<Int>()
        val left = (bitmap.width * .035).toInt()
        val right = (bitmap.width * .96).toInt()
        for (y in (bitmap.height * .23).toInt()..(bitmap.height * .82).toInt()) {
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            var count = 0
            var sampled = 0
            for (x in left..right step 3) {
                if (dark(pixels[x])) count++
                sampled++
            }
            if (count > sampled * .88) horizontal += y
        }
        val lines = centers(horizontal)
        val runs =
            (0 until lines.lastIndex).map { start ->
                val gap = lines[start + 1] - lines[start]
                if (gap !in (bitmap.height * .008).toInt()..(bitmap.height * .025).toInt())
                    emptyList()
                else {
                    val result = mutableListOf(lines[start])
                    for (i in start + 1 until lines.size) {
                        if (abs(lines[i] - result.last() - gap) > gap * .25 || result.size == 32)
                            break
                        result += lines[i]
                    }
                    result
                }
            }
        val rows = runs.maxByOrNull { it.size } ?: emptyList()
        require(rows.size in 29..32) { "PAYROLL_GRID" }
        val counts = IntArray(bitmap.width)
        var sampled = 0
        for (y in rows.first() + 5 until rows.last() - 5 step 3) {
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in left..right) if (dark(pixels[x])) counts[x]++
            sampled++
        }
        val verticals = centers((left..right).filter { counts[it] > sampled * .8 })
        require(verticals.size == 13) { "PAYROLL_GRID" }
        val timeWidths = verticals.drop(1).take(11).zipWithNext().map { (a, b) -> b - a }
        val width = timeWidths.average()
        require(
            timeWidths.all { abs(it - width) < width * .25 } &&
                verticals.last() - verticals[11] > width * 3
        ) {
            "PAYROLL_GRID"
        }
        return Grid(rows, verticals)
    }

    suspend fun preview(file: File, source: Box? = null): Bitmap =
        withContext(Dispatchers.Default) {
            val bitmap = render(file, 1600)
            if (source == null) return@withContext bitmap
            try {
                val left = (source.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                val top = (source.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                val right =
                    (source.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
                val bottom =
                    (source.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
                val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                if (cropped === bitmap) bitmap.copy(Bitmap.Config.ARGB_8888, false) else cropped
            } finally {
                bitmap.recycle()
            }
        }

    private fun render(file: File, width: Int): Bitmap {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount == 1) { "PAYROLL_PAGES" }
                renderer.openPage(0).use { page ->
                    require(
                        page.width > 0 &&
                            page.height > 0 &&
                            page.height.toDouble() / page.width in 1.1..1.8
                    ) {
                        "PAYROLL_GRID"
                    }
                    val height = (width.toLong() * page.height / page.width).toInt()
                    require(height in 1..4400) { "PAYROLL_GRID" }
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bitmap
                    } catch (error: Throwable) {
                        bitmap.recycle()
                        throw error
                    }
                }
            }
        }
    }
}
