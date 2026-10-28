package it.turni.app

import it.turni.app.calendar.upcomingWorkShifts
import it.turni.app.data.ShiftEntity
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class UpcomingShiftsTest {
    private val base =
        ShiftEntity(
            "work",
            "week",
            "employee",
            "2026-10-28",
            "20:00",
            "08:00",
            true,
            null,
            "WORK_SHIFT",
            1.0,
            1,
            1,
            "",
            1,
            1,
        )

    @Test
    fun lateEveningExcludesCurrentNightShiftAndSortsNextStarts() {
        val tomorrow = base.copy(id = "tomorrow", date = "2026-10-29", startTime = "18:00")
        val todayLater = base.copy(id = "later", startTime = "23:00")
        val result =
            upcomingWorkShifts(
                listOf(tomorrow, base, todayLater),
                LocalDateTime.parse("2026-10-28T21:49"),
            )
        assertEquals(listOf("later", "tomorrow"), result.map { it.id })
    }

    @Test
    fun startBoundaryMidnightAndInvalidRowsNeverShowStartedShifts() {
        val tomorrow = base.copy(id = "tomorrow", date = "2026-10-29", startTime = "18:00")
        val rows =
            listOf(
                base,
                tomorrow,
                base.copy(id = "rest", type = "CODE"),
                base.copy(id = "bad", startTime = "invalid"),
                base.copy(id = "missing", endTime = null),
            )
        assertEquals(
            listOf("tomorrow"),
            upcomingWorkShifts(rows, LocalDateTime.parse("2026-10-28T20:00")).map { it.id },
        )
        assertEquals(
            listOf("tomorrow"),
            upcomingWorkShifts(rows, LocalDateTime.parse("2026-10-29T00:01")).map { it.id },
        )
        assertTrue(upcomingWorkShifts(rows, LocalDateTime.parse("2026-10-29T18:00")).isEmpty())
    }
}
