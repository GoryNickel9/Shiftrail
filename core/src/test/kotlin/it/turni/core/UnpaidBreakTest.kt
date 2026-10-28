package it.turni.core

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class UnpaidBreakTest {
    private val date = LocalDate.of(2026, 10, 31)
    private val month = HoursWindow(date.withDayOfMonth(1), date)

    private fun pause(start: String, end: String) =
        UnpaidBreak(LocalDateTime.parse(start), LocalDateTime.parse(end))

    private fun night() =
        WorkPeriod("work", date, LocalTime.of(22, 0), LocalTime.of(6, 0), WorkSource.SCHEDULE)

    @Test
    fun breaksSplitAcrossMidnightMonthAndWeek() {
        val period =
            night()
                .copy(
                    breaks =
                        listOf(
                            pause("2026-10-31T23:45", "2026-11-01T00:15"),
                            pause("2026-11-01T02:00", "2026-11-01T02:30"),
                        )
                )
        period.validateBreaks()
        val october = WorkingHours.summarize(listOf(period), month)
        val november =
            WorkingHours.summarize(
                listOf(period),
                HoursWindow(date.plusDays(1), date.plusMonths(1)),
            )
        assertEquals(105L, october.totalMinutes)
        assertEquals(315L, november.totalMinutes)
        assertEquals(315L, november.nightMinutes)
        assertEquals(420L, october.totalMinutes + november.totalMinutes)
    }

    @Test
    fun subtractBreaksBeforeUnionAndKeepOtherPaidWork() {
        val work = night().copy(breaks = listOf(pause("2026-11-01T02:00", "2026-11-01T03:00")))
        val extra =
            WorkPeriod(
                "extra",
                date.plusDays(1),
                LocalTime.of(2, 30),
                LocalTime.of(4, 0),
                WorkSource.EXTRA,
                breaks = listOf(pause("2026-11-01T03:00", "2026-11-01T03:30")),
            )
        val window = HoursWindow(date, date.plusDays(1))
        val result = WorkingHours.summarize(listOf(work, extra), window)
        assertEquals(420L, result.workMinutes)
        assertEquals(30L, result.extraMinutes)
        assertEquals(450L, result.totalMinutes)
        assertEquals(330L, result.nightMinutes)
        assertEquals(result, WorkingHours.summarize(listOf(extra, work), window))
        // Independent minute-level oracle for union, boundaries and night classification.
        val paid =
            (0 until 1440).map { date.atStartOfDay().plusMinutes(it.toLong()) } +
                (0 until 1440).map { date.plusDays(1).atStartOfDay().plusMinutes(it.toLong()) }
        val counted = paid.filter { at ->
            listOf(work, extra).any {
                at >= it.startDateTime &&
                    at < it.endDateTime &&
                    it.breaks.none { b -> at >= b.start && at < b.end }
            }
        }
        assertEquals(counted.size.toLong(), result.totalMinutes)
        assertEquals(counted.count { it.hour < 6 }.toLong(), result.nightMinutes)
    }

    @Test
    fun rejectOverlapOutsideAndZeroLength() {
        assertThrows(IllegalArgumentException::class.java) {
            pause("2026-11-01T02:00", "2026-11-01T02:00")
        }
        assertThrows(IllegalArgumentException::class.java) {
            night()
                .copy(breaks = listOf(pause("2026-11-01T05:45", "2026-11-01T06:15")))
                .validateBreaks()
        }
        assertThrows(IllegalArgumentException::class.java) {
            night()
                .copy(
                    breaks =
                        listOf(
                            pause("2026-11-01T02:00", "2026-11-01T03:00"),
                            pause("2026-11-01T02:30", "2026-11-01T03:30"),
                        )
                )
                .validateBreaks()
        }
    }

    @Test
    fun adjacentAndWholeShiftBreaksAreValid() {
        val work =
            night()
                .copy(
                    breaks =
                        listOf(
                            pause("2026-10-31T22:00", "2026-11-01T00:00"),
                            pause("2026-11-01T00:00", "2026-11-01T06:00"),
                        )
                )
        work.validateBreaks()
        assertEquals(
            0L,
            WorkingHours.summarize(listOf(work), HoursWindow(date, date.plusDays(1))).totalMinutes,
        )
    }

    @Test
    fun externalEditKeepsInvalidBreakForReviewWithoutSubtractingIt() {
        val work =
            night()
                .copy(
                    end = LocalTime.of(1, 0),
                    breaks = listOf(pause("2026-11-01T02:00", "2026-11-01T03:00")),
                )
        assertTrue(work.validBreaks.isEmpty())
        assertEquals(1, work.breaks.size)
        assertEquals(
            180L,
            WorkingHours.summarize(listOf(work), HoursWindow(date, date.plusDays(1))).totalMinutes,
        )
    }
}
