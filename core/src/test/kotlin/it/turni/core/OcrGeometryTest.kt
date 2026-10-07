package it.turni.core

import org.junit.Assert.*
import org.junit.Test

class OcrGeometryTest {
    @Test
    fun validButDifferentTimesMustBeReviewedRatherThanGuessed() {
        val date = java.time.LocalDate.of(2026, 11, 16)
        val a = ShiftParser.parse("22-01", date)
        val b = ShiftParser.parse("2-01", date)
        val readings = OcrGeometry.conflictingReadings(a, b)
        assertEquals(listOf("22-01", "2-01"), readings)
        val ambiguous = ShiftParser.parse(readings.joinToString(" / "), date)
        assertEquals(CellType.UNKNOWN, ambiguous.type)
        assertTrue(ambiguous.needsReview)
        assertTrue(OcrGeometry.conflictingReadings(a, ShiftParser.parse("22-1", date)).isEmpty())
        assertTrue(OcrGeometry.conflictingReadings(a, ShiftParser.parse("22+01", date)).isEmpty())
    }

    @Test
    fun conflictingReadingsOfSameWordKeepHigherConfidence() {
        val a = OcrToken("LUNEDI", Box(0.3f, 0.4f, 0.4f, 0.42f), 0.95f)
        val b = a.copy(text = "LUNEOI", confidence = 0.8f)
        assertEquals(listOf(a), OcrGeometry.merge(listOf(b, a)))
    }

    @Test
    fun overlappingValidTimeAlternativesAreNotSilentlyDiscarded() {
        val a = OcrToken("18:30-21:30", Box(0.3f, 0.4f, 0.4f, 0.42f), 0.95f)
        val b = a.copy(text = "18:30-2:30", confidence = 0.8f)
        val merged = OcrGeometry.merge(listOf(b, a)).single()
        assertEquals("18:30-21:30 / 18:30-2:30", merged.text)
        val ambiguous = ShiftParser.parse(merged.text, java.time.LocalDate.of(2026, 11, 16))
        assertTrue(ambiguous.needsReview)
        assertEquals(
            2,
            OcrGeometry.conflictingReadings(ambiguous, ShiftParser.parse(a.text, ambiguous.date))
                .size,
        )
    }

    @Test
    fun cropCoordinatesRemainInSourceSpace() {
        assertEquals(
            Box(0.3f, 0.4f, 0.5f, 0.6f),
            OcrGeometry.map(Box(0f, 0f, 1f, 1f), Box(0.3f, 0.4f, 0.5f, 0.6f)),
        )
        val center = OcrGeometry.map(Box(0.25f, 0.25f, 0.75f, 0.75f), Box(0f, 0f, 0.8f, 0.8f))
        assertEquals(0.4f, center.centerX, 0.00001f)
        assertEquals(0.4f, center.centerY, 0.00001f)
    }

    @Test
    fun overlappingTilesDoNotDuplicateTextButKeepIdenticalAdjacentRows() {
        val first = OcrToken("19-01", Box(0.3f, 0.4f, 0.4f, 0.42f), 0.8f)
        val duplicate = first.copy(box = Box(0.301f, 0.401f, 0.401f, 0.421f), confidence = 0.95f)
        val nextRow = first.copy(box = Box(0.3f, 0.43f, 0.4f, 0.45f))
        val anotherDay = first.copy(box = Box(0.5f, 0.4f, 0.6f, 0.42f))
        val result = OcrGeometry.merge(listOf(first, duplicate, nextRow, anotherDay))
        assertEquals(3, result.size)
        assertTrue(duplicate in result)
        assertTrue(nextRow in result)
        assertTrue(anotherDay in result)
        assertFalse(first in result)
    }
}
