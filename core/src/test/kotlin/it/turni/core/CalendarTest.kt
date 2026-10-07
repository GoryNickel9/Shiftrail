package it.turni.core

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CalendarTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val options = EventOptions("Turno di lavoro")

    private fun event(raw: String) =
        EventFactory.create(
            ShiftParser.parse(raw, monday),
            emptyList(),
            options,
            ZoneId.of("Europe/Rome"),
        )!!

    @Test
    fun durationAndTimezone() {
        val event = event("19-01")
        assertEquals(6 * 3600000L, event.endMillis - event.startMillis)
        assertEquals("Europe/Rome", event.timezone)
    }

    @Test
    fun repeatedImportAndModification() {
        val first = event("19-01")
        val saved = listOf(SavedEvent(monday, first.contentSignature, 42, 1))
        assertEquals(
            ChangeKind.UNCHANGED,
            SyncPlanner.plan(listOf(first), saved, 1, setOf(42)).single().kind,
        )
        assertEquals(
            ChangeKind.UPDATE,
            SyncPlanner.plan(listOf(event("18:30-01")), saved, 1, setOf(42)).single().kind,
        )
        assertEquals(
            ChangeKind.MISSING,
            SyncPlanner.plan(emptyList(), saved, 1, setOf(42)).single().kind,
        )
        assertEquals(
            ChangeKind.RECREATE,
            SyncPlanner.plan(listOf(first), saved, 1, emptySet()).single().kind,
        )
        assertEquals(
            ChangeKind.INSERT,
            SyncPlanner.plan(listOf(first), saved, 2, emptySet()).single().kind,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun multipleEventsSameDayAreNotSilentlyMerged() {
        SyncPlanner.plan(listOf(event("12-18"), event("19-01")), emptyList(), 1, emptySet())
    }

    @Test
    fun restCreatesFixedAllDayEventEvenWithAnOldCustomLegend() {
        val cell = ShiftParser.parse("X", monday)
        val expected = EventFactory.create(cell, emptyList(), options, ZoneId.of("Europe/Rome"))!!
        assertEquals("Giorno di riposo", expected.title)
        assertTrue(expected.allDay)
        assertEquals("UTC", expected.timezone)
        assertEquals(
            monday.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
            expected.startMillis,
        )
        assertEquals(86400000L, expected.endMillis - expected.startMillis)
        for (behavior in CalendarBehavior.entries) assertEquals(
            expected,
            EventFactory.create(
                cell,
                listOf(
                    CodeMapping(
                        "X",
                        "Vecchia legenda",
                        behavior,
                        java.time.LocalTime.of(9, 0),
                        java.time.LocalTime.of(17, 0),
                    )
                ),
                options,
                ZoneId.of("Europe/Rome"),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun oldUnsupportedCodeCannotExportEvenIfMarkedReviewed() {
        EventFactory.create(
            ShiftCell(monday, "FE", CellType.CODE, code = "FE", reviewed = true),
            listOf(CodeMapping("FE", "Ferie", CalendarBehavior.ALL_DAY)),
            options,
            ZoneId.of("Europe/Rome"),
        )
    }

    @Test
    fun restKeepsTheCalendarDateAcrossDaylightSavingChanges() {
        for (date in listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25))) {
            val cell = ShiftParser.parse("X", date)
            val rome = EventFactory.create(cell, emptyList(), options, ZoneId.of("Europe/Rome"))!!
            val newYork =
                EventFactory.create(cell, emptyList(), options, ZoneId.of("America/New_York"))!!
            assertEquals(rome, newYork)
            assertEquals(
                date,
                java.time.Instant.ofEpochMilli(rome.startMillis)
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate(),
            )
            assertEquals(
                date.plusDays(1),
                java.time.Instant.ofEpochMilli(rome.endMillis)
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate(),
            )
        }
    }

    @Test
    fun markersStableAndSeparateEmployees() {
        assertEquals(eventMarker("A", monday, 1), eventMarker("A", monday, 1))
        assertNotEquals(eventMarker("A", monday, 1), eventMarker("B", monday, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun daylightSavingGapRequiresManualCorrection() {
        EventFactory.create(
            ShiftParser.parse("02:30-04:30", LocalDate.of(2026, 3, 29)),
            emptyList(),
            options,
            ZoneId.of("Europe/Rome"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun daylightSavingOverlapRequiresManualCorrection() {
        EventFactory.create(
            ShiftParser.parse("02:30-04:30", LocalDate.of(2026, 10, 25)),
            emptyList(),
            options,
            ZoneId.of("Europe/Rome"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun lowConfidenceCannotExportWithoutReview() {
        EventFactory.create(
            ShiftParser.parse("19-01", monday, 0.6),
            emptyList(),
            options,
            ZoneId.of("UTC"),
        )
    }

    @Test
    fun lowConfidenceAcknowledgementAllowsExport() {
        assertNotNull(
            EventFactory.create(
                ShiftParser.parse("19-01", monday, 0.6).copy(reviewed = true),
                emptyList(),
                options,
                ZoneId.of("UTC"),
            )
        )
    }
}
