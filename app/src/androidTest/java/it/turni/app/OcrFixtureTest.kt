package it.turni.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.imageprocessing.ImageProcessor
import it.turni.app.ocr.*
import it.turni.core.*
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrFixtureTest {
    @Test
    fun smallTextWithOffsetHeadersAndAttachedCountersIsReadSafely() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monday = LocalDate.of(2026, 12, 7)
        val shifts =
            listOf("11:15-17:15", "20-23", "06:45-12:45", "07-13", "17-01", "13-16", "23:30-02:30")
        val source = Bitmap.createBitmap(1840, 360, Bitmap.Config.ARGB_8888)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 24f
            }
        Canvas(source).apply {
            drawColor(Color.WHITE)
            paint.textAlign = Paint.Align.CENTER
            listOf("LUNEDI", "MARTEDI", "MERCOLEDI", "GIOVEDI", "VENERDI", "SABATO", "DOMENICA")
                .forEachIndexed { i, day ->
                    val x = 500f + 200f * i
                    drawText(day, x + 30f, 65f, paint)
                    drawText("${7 + i}/12/2026", x + 30f, 100f, paint)
                    drawLine(x - 100f, 40f, x - 100f, 315f, paint)
                    drawLine(x + 76f, 120f, x + 76f, 315f, paint)
                }
            listOf("MARCO NERI", "ELISA FERRI", "ANNA ROSSI").forEachIndexed { r, name ->
                val y = 210f + 40f * r
                paint.textAlign = Paint.Align.LEFT
                drawText(name, 30f, y, paint)
                paint.textAlign = Paint.Align.CENTER
                (if (r == 1) shifts else List(7) { "18-22" }).forEachIndexed { c, raw ->
                    val x = 500f + 200f * c
                    drawText(raw, x, y, paint)
                    drawText("6", x + 90f, y, paint)
                }
                drawLine(20f, y + 12f, 1830f, y + 12f, paint)
            }
        }
        val bitmap = Bitmap.createScaledBitmap(source, 920, 180, true)
        source.recycle()
        try {
            val extractor = LocalScheduleExtractor(ImageProcessor(instrumentation.targetContext))
            extractor.onReading = { stage, table ->
                android.util.Log.i("TurniOffset", "$stage cells=${table.cells.map { it.raw }}")
            }
            val result =
                extractor.extract(ImageInput(bitmap, EmployeeProfile("Elisa Ferri"), monday))
            val table =
                TableParser()
                    .extract(
                        result.tokens,
                        EmployeeProfile("Elisa Ferri"),
                        monday,
                        selectedRow = result.table.selected,
                        confirmedMonday = monday,
                    )
            assertNotNull(result.table.selected)
            assertEquals(7, table.cells.size)
            android.util.Log.i("TurniOffset", "final cells=${table.cells.map { it.raw }}")
            shifts.forEachIndexed { i, raw ->
                val cell = table.cells[i]
                val expected = ShiftParser.parse(raw, monday.plusDays(i.toLong())).signature()
                if (i == shifts.lastIndex && cell.type == CellType.UNKNOWN) {
                    // OCR can attach the counter to the last minute digit without whitespace.
                    // This must stay blocked rather than inventing a boundary inside the number.
                    assertTrue(cell.raw, cell.raw.contains("23:30"))
                    assertTrue(cell.needsReview)
                } else assertEquals(expected, cell.signature())
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun anotherLayoutNameWeekAndShiftsAreReadFromPixels() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monday = LocalDate.of(2026, 11, 16)
        val shifts = listOf("07:15-13:15", "FE", "18-00", "X", "09-12", "14:30-18:30", "20-02")
        val bitmap = Bitmap.createBitmap(1600, 480, Bitmap.Config.ARGB_8888)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 22f
            }
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val days =
                listOf("LUNEDI", "MARTEDI", "MERCOLEDI", "GIOVEDI", "VENERDI", "SABATO", "DOMENICA")
            paint.textAlign = Paint.Align.CENTER
            days.forEachIndexed { i, day ->
                val x = 420f + 180f * i
                drawText(day, x, 75f, paint)
                drawText("${16 + i}/11/2026", x, 110f, paint)
            }
            listOf("MARTA NERI", "GIULIA SERRA", "DARIO TESTA").forEachIndexed { r, name ->
                val y = 250f + 60f * r
                paint.textAlign = Paint.Align.LEFT
                drawText(name, 30f, y, paint)
                paint.textAlign = Paint.Align.CENTER
                (if (r == 1) shifts else List(7) { "18-22" }).forEachIndexed { c, raw ->
                    drawText(raw, 420f + 180f * c, y, paint)
                }
                drawLine(20f, y + 15f, 1595f, y + 15f, paint)
            }
            (0..7).forEach { i -> drawLine(330f + 180f * i, 45f, 330f + 180f * i, 405f, paint) }
        }
        try {
            val result =
                LocalScheduleExtractor(ImageProcessor(instrumentation.targetContext))
                    .extract(ImageInput(bitmap, EmployeeProfile("Giulia Serra"), monday))
            assertTrue(result.table.issues.toString(), result.table.issues.isEmpty())
            assertEquals(
                shifts.mapIndexed { i, raw ->
                    ShiftParser.parse(raw, monday.plusDays(i.toLong())).signature()
                },
                result.table.cells.map { it.signature() },
            )
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun smallTextRosterKeepsCorrectRow() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val source =
            instrumentation.context.assets.open("synthetic-roster.png").use {
                BitmapFactory.decodeStream(it)
            }
        val bitmap = Bitmap.createScaledBitmap(source, 1000, 750, true)
        source.recycle()
        try {
            val result =
                LocalScheduleExtractor(ImageProcessor(instrumentation.targetContext))
                    .extract(
                        ImageInput(
                            bitmap,
                            EmployeeProfile("Luca Baldeddu"),
                            LocalDate.of(2026, 10, 7),
                        )
                    )
            assertNotNull(result.table.selected)
            // Tiny dates can require the same explicit Monday confirmation offered by the UI.
            val table =
                TableParser()
                    .extract(
                        result.tokens,
                        EmployeeProfile("Luca Baldeddu"),
                        LocalDate.of(2026, 10, 7),
                        selectedRow = result.table.selected,
                        confirmedMonday = LocalDate.of(2026, 10, 5),
                    )
            assertEquals(7, table.columns.size)
            assertEquals(7, table.cells.size)
            val shifts =
                listOf("19-01", "X", "23:45-5:45", "FE", "11:30-17:30", "6:30-12:30", "A.G.")
            table.cells.forEachIndexed { index, cell ->
                val expected =
                    ShiftParser.parse(
                            shifts[index],
                            LocalDate.of(2026, 10, 5).plusDays(index.toLong()),
                        )
                        .signature()
                if (cell.type == CellType.UNKNOWN) {
                    assertTrue(
                        cell.raw,
                        cell.raw.split(" / ").any {
                            ShiftParser.parse(it, cell.date).signature() == expected
                        },
                    )
                    assertTrue(cell.needsReview)
                } else assertEquals(expected, cell.signature())
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun bundledOcrExtractsOnlyEmployeeRow() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap =
            instrumentation.context.assets.open("synthetic-roster.png").use {
                BitmapFactory.decodeStream(it)
            }
        val result =
            LocalScheduleExtractor(ImageProcessor(instrumentation.targetContext))
                .extract(
                    ImageInput(bitmap, EmployeeProfile("Luca Baldeddu"), LocalDate.of(2026, 10, 7))
                )
        android.util.Log.i(
            "TurniFixture",
            "OCR elapsedMillis=${result.elapsedMillis} tokens=${result.tokens.size}",
        )
        assertNotNull(result.table.selected)
        assertEquals(7, result.table.cells.size)
        assertEquals(LocalTime.of(19, 0), result.table.cells[0].start)
        assertEquals(LocalTime.of(1, 0), result.table.cells[0].end)
        assertEquals(LocalTime.of(23, 45), result.table.cells[2].start)
        assertTrue(result.table.cells[2].endNextDay)
        assertEquals(CellType.UNKNOWN, result.table.cells[3].type)
        assertTrue(result.table.cells[3].raw.contains("FE"))
        assertTrue(result.table.cells[3].needsReview)
        bitmap.recycle()
    }
}
