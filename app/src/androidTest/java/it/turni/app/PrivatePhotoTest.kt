package it.turni.app

import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.imageprocessing.ImageProcessor
import it.turni.app.ocr.*
import it.turni.core.*
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in local regression: the private image is never packaged in an APK or committed. */
@RunWith(AndroidJUnit4::class)
class PrivatePhotoTest {
    @Test
    fun denseRosterPhoto() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("privatePhoto") == "true")
        val employee = requireNotNull(arguments.getString("employee"))
        val monday = LocalDate.parse(requireNotNull(arguments.getString("monday")))
        val shifts = requireNotNull(arguments.getString("shifts")).split(',')
        require(shifts.size == 7 && monday.dayOfWeek.value == 1)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap =
            ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand(
                        "cat /data/local/tmp/turni-regression.png"
                    )
                )
                .use { requireNotNull(BitmapFactory.decodeStream(it)) }
        val result =
            LocalScheduleExtractor(ImageProcessor(instrumentation.targetContext))
                .extract(ImageInput(bitmap, EmployeeProfile(employee), monday))
        android.util.Log.i(
            "TurniPrivate",
            "tokens=${result.tokens.size} issues=${result.table.issues} columns=${result.table.columns.size} selected=${result.table.selected?.label} cells=${result.table.cells.map { it.raw }}",
        )
        result.tokens
            .filter {
                ItalianDateParser.weekday(it.text) != null ||
                    TextNormalizer.similarity(it.text, employee) > 0.65
            }
            .forEach {
                android.util.Log.i("TurniPrivate", "anchor=${it.text} box=${it.box}")
            }
        assertNotNull(result.table.selected)
        assertEquals(7, result.table.cells.size)
        assertEquals(monday, result.table.cells.first().date)
        val expected = shifts.mapIndexed { index, raw ->
            ShiftParser.parse(raw, monday.plusDays(index.toLong())).signature()
        }
        result.table.cells.forEachIndexed { index, cell ->
            if (cell.type == CellType.UNKNOWN) {
                assertTrue(
                    "Expected reading must remain visible in an ambiguous cell",
                    cell.raw.split(" / ").any {
                        ShiftParser.parse(it, cell.date).signature() == expected[index]
                    },
                )
                assertTrue(cell.needsReview)
            } else assertEquals(expected[index], cell.signature())
        }
        assertFalse(ExtractionIssue.INVALID_DATES in result.table.issues)
        bitmap.recycle()
    }
}
