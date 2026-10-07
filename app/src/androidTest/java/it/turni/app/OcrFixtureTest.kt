package it.turni.app

import android.graphics.BitmapFactory
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
        assertEquals("FE", result.table.cells[3].code)
        bitmap.recycle()
    }
}
