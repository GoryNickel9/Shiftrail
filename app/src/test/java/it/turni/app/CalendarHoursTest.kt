package it.turni.app

import it.turni.app.calendar.*
import it.turni.app.data.ShiftEntity
import it.turni.core.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class CalendarHoursTest {
    @Test
    fun homeAndHoursShareTheCurrentDatesAndTimesAndOmitRemovedEvents() {
        val moved =
            event.copy(start = millis(date.plusDays(1), 12), end = millis(date.plusDays(1), 15))
        val snapshot = calendarSchedule(listOf(shift), mapOf(1L to moved), ZoneOffset.UTC)
        assertEquals(snapshot.periods.single().date.toString(), snapshot.shifts.single().date)
        assertEquals(snapshot.periods.single().start.toString(), snapshot.shifts.single().startTime)
        assertEquals(snapshot.periods.single().end.toString(), snapshot.shifts.single().endTime)
        assertFalse(snapshot.shifts.single().endNextDay)
        assertTrue(calendarSchedule(listOf(shift), emptyMap(), ZoneOffset.UTC).shifts.isEmpty())
        val rest =
            calendarSchedule(
                listOf(shift),
                mapOf(1L to moved.copy(allDay = true)),
                ZoneId.of("Pacific/Honolulu"),
            )
        assertEquals(date.plusDays(1).toString(), rest.shifts.single().date)
        assertEquals("X", rest.shifts.single().code)
        assertTrue(rest.periods.isEmpty())
    }

    @Test
    fun calendarSecondsAreKeptUntilTheDurationIsConvertedToWholeMinutes() {
        val seconds = event.copy(start = millis(date, 19) + 45000, end = millis(date, 20) + 15000)
        val period =
            calendarWorkPeriods(listOf(shift), mapOf(1L to seconds), ZoneOffset.UTC).single()
        assertEquals(
            59L,
            WorkingHours.summarize(listOf(period), HoursWindow(date, date)).totalMinutes,
        )
    }

    private val date = LocalDate.of(2026, 10, 5)
    private val shift =
        ShiftEntity(
            "shift",
            "week",
            "employee",
            date.toString(),
            "19:00",
            "01:00",
            true,
            null,
            "WORK_SHIFT",
            .95,
            1,
            2,
            "signature",
            0,
            1,
        )
    private val marker = eventMarker(shift.employeeId, date, shift.calendarId)

    private fun millis(day: LocalDate, hour: Int) =
        day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val event =
        ProviderEvent(
            1,
            2,
            marker,
            millis(date, 22),
            millis(date.plusDays(1), 1),
            "Lavoro",
            "",
            false,
        )

    @Test
    fun liveTimesAndMovedDatesReplaceTheImportedSnapshot() {
        val period = calendarWorkPeriods(listOf(shift), mapOf(1L to event), ZoneOffset.UTC).single()
        assertEquals(LocalTime.of(22, 0), period.start)
        assertEquals(
            180L,
            WorkingHours.summarize(listOf(period), HoursWindow(date, date.plusDays(1)))
                .totalMinutes,
        )
        val moved =
            event.copy(start = millis(date.plusDays(10), 20), end = millis(date.plusDays(11), 2))
        val movedPeriod =
            calendarWorkPeriods(listOf(shift), mapOf(1L to moved), ZoneOffset.UTC).single()
        assertEquals(date.plusDays(10), movedPeriod.date)
        assertEquals(date.plusDays(11), movedPeriod.endDate)
        assertEquals(
            0L,
            WorkingHours.summarize(listOf(movedPeriod), HoursWindow(date, date.plusDays(1)))
                .totalMinutes,
        )
    }

    @Test
    fun removedAndAllDayEventsDoNotCreateWorkHours() {
        assertTrue(calendarWorkPeriods(listOf(shift), emptyMap(), ZoneOffset.UTC).isEmpty())
        assertTrue(
            calendarWorkPeriods(
                    listOf(shift),
                    mapOf(1L to event.copy(allDay = true)),
                    ZoneOffset.UTC,
                )
                .isEmpty()
        )
    }

    @Test
    fun ownershipAndDurationAreValidated() {
        assertThrows(IllegalArgumentException::class.java) {
            calendarWorkPeriods(
                listOf(shift),
                mapOf(1L to event.copy(calendarId = 3)),
                ZoneOffset.UTC,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            calendarWorkPeriods(
                listOf(shift),
                mapOf(1L to event.copy(description = "personal")),
                ZoneOffset.UTC,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            calendarWorkPeriods(
                listOf(shift),
                mapOf(1L to event.copy(end = event.start)),
                ZoneOffset.UTC,
            )
        }
    }
}
