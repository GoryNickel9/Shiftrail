package it.turni.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class InvariantsTest {
    @Test
    fun randomizedTimeRangesPreserveDurationAndStableSignatures() {
        val random = Random(42)
        val date = LocalDate.of(2026, 10, 5)
        repeat(1000) {
            val start = random.nextInt(1440)
            var end = random.nextInt(1440)
            if (end == start) end = (end + 1) % 1440
            val first = LocalTime.of(start / 60, start % 60)
            val last = LocalTime.of(end / 60, end % 60)
            val cell = ShiftParser.parse("$first-$last", date)
            assertEquals(CellType.WORK_SHIFT, cell.type)
            val expectedMinutes = (end - start + 1440) % 1440
            assertEquals(
                expectedMinutes.toLong(),
                Duration.between(date.atTime(first), cell.endDate.atTime(last)).toMinutes(),
            )
            val event =
                EventFactory.create(cell, emptyList(), EventOptions("Turno"), ZoneId.of("UTC"))!!
            assertEquals(expectedMinutes * 60000L, event.endMillis - event.startMillis)
            assertEquals(cell.signature(), ShiftParser.parse("$first-$last", date).signature())
            assertEquals(
                ChangeKind.UNCHANGED,
                SyncPlanner.plan(
                        listOf(event),
                        listOf(SavedEvent(date, event.contentSignature, 1, 1)),
                        1,
                        setOf(1),
                    )
                    .single()
                    .kind,
            )
        }
    }

    @Test
    fun corruptGeometryIsRejected() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { number ->
            assertThrows(IllegalArgumentException::class.java) { Box(number, 0f, 1f, 1f) }
        }
        assertThrows(IllegalArgumentException::class.java) { Box(1f, 0f, 0f, 1f) }
    }
}
