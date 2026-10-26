package it.turni.app

import it.turni.app.calendar.*
import it.turni.app.data.*
import it.turni.core.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ExtraHoursTest {
    private val date = LocalDate.of(2020, 10, 8)
    private val shift =
        ShiftEntity(
            "shift",
            "week",
            "employee",
            date.toString(),
            "16:00",
            "18:00",
            false,
            null,
            CellType.WORK_SHIFT.name,
            1.0,
            1,
            2,
            "signature",
            0,
            0,
        )

    private fun extra(id: String, start: String, end: String) =
        ExtraWorkEntity(id, "employee", date.toString(), start, end, 0)

    private val extras = listOf(extra("before", "13:00", "16:00"), extra("after", "18:00", "20:00"))

    @Test
    fun pastShiftExpandsInHomeWhileHoursKeepBaseAndExtraSeparate() {
        val expanded = extraHoursCell(shift, extras)
        assertEquals(LocalTime.of(13, 0), expanded.start)
        assertEquals(LocalTime.of(20, 0), expanded.end)
        val event =
            requireNotNull(
                EventFactory.create(expanded, emptyList(), EventOptions(), ZoneOffset.UTC)
            )
        val provider =
            ProviderEvent(
                1,
                2,
                eventMarker("employee", date, 2),
                event.startMillis,
                event.endMillis,
                event.title,
                "",
                false,
            )
        val schedule =
            calendarSchedule(listOf(shift), mapOf(1L to provider), ZoneOffset.UTC, extras)
        assertEquals("13:00", schedule.shifts.single().startTime)
        assertEquals("20:00", schedule.shifts.single().endTime)
        assertEquals(LocalTime.of(16, 0), schedule.periods.single().start)
        val summary =
            WorkingHours.summarize(
                schedule.periods + extras.map { it.period() },
                HoursWindow(date, date),
            )
        assertEquals(120L, summary.workMinutes)
        assertEquals(300L, summary.extraMinutes)
        assertEquals(420L, summary.totalMinutes)
        assertEquals("16:00", shift.startTime)
    }

    @Test
    fun editingDeletingAndFallbackProjectionUseTheSameBase() {
        assertEquals("13:00", extraHoursSummary(shift, extras).startTime)
        assertEquals(LocalTime.of(18, 0), extraHoursCell(shift, extras.take(1)).end)
        assertEquals(LocalTime.of(16, 0), extraHoursCell(shift, extras.drop(1)).start)
        assertEquals(baseCell(shift), extraHoursCell(shift, emptyList()))
        val edited = listOf(extras.first().copy(startTime = "12:00"), extras.last())
        assertEquals(LocalTime.NOON, extraHoursCell(shift, edited).start)
    }

    @Test
    fun overnightUsesDatesAndRejectsEnvelopesOf24HoursOrMore() {
        val night = shift.copy(startTime = "22:00", endTime = "02:00", endNextDay = true)
        val expanded = extraHoursCell(night, listOf(extra("early", "20:00", "22:00")))
        assertEquals(LocalTime.of(20, 0), expanded.start)
        assertEquals(date.plusDays(1), expanded.endDate)
        assertThrows(IllegalArgumentException::class.java) {
            extraHoursCell(night, listOf(extra("tooEarly", "02:00", "04:00")))
        }
    }

    @Test
    fun extraOnRestOrEmptyDayDoesNotBecomeScheduledHours() {
        for (type in listOf(CellType.CODE, CellType.EMPTY)) {
            val base =
                shift.copy(
                    type = type.name,
                    code = if (type == CellType.CODE) "X" else null,
                    startTime = null,
                    endTime = null,
                )
            val event =
                requireNotNull(
                    EventFactory.create(
                        extraHoursCell(base, extras),
                        emptyList(),
                        EventOptions(),
                        ZoneOffset.UTC,
                    )
                )
            val provider =
                ProviderEvent(
                    1,
                    2,
                    eventMarker("employee", date, 2),
                    event.startMillis,
                    event.endMillis,
                    event.title,
                    "",
                    false,
                )
            val schedule =
                calendarSchedule(listOf(base), mapOf(1L to provider), ZoneOffset.UTC, extras)
            assertEquals("13:00", schedule.shifts.single().startTime)
            assertTrue(schedule.periods.isEmpty())
        }
    }
}
