package it.turni.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class WorkingHoursTest {
    private fun period(
        id: String,
        date: String,
        start: String,
        end: String,
        source: WorkSource = WorkSource.SCHEDULE,
    ) = WorkPeriod(id, LocalDate.parse(date), LocalTime.parse(start), LocalTime.parse(end), source)

    private val october = HoursWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    @Test
    fun workAndManualExtraHaveSeparateTotalsAndRestHasNoPeriod() {
        val result =
            WorkingHours.summarize(
                listOf(
                    period("work", "2026-10-05", "19:00", "01:00"),
                    period("extra", "2026-10-06", "10:15", "12:45", WorkSource.EXTRA),
                ),
                october,
            )
        assertEquals(360L, result.workMinutes)
        assertEquals(150L, result.extraMinutes)
        assertEquals(510L, result.totalMinutes)
    }

    @Test
    fun monthBoundarySplitsBothIncomingAndOutgoingNightWork() {
        val periods =
            listOf(
                period("incoming", "2026-09-30", "23:00", "02:00"),
                period("outgoing", "2026-10-31", "22:30", "01:30"),
            )
        val octoberResult = WorkingHours.summarize(periods, october)
        assertEquals(listOf(120L, 90L), octoberResult.entries.map { it.minutes })
        assertEquals(210L, octoberResult.totalMinutes)
        val novemberResult =
            WorkingHours.summarize(
                periods,
                HoursWindow(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
            )
        assertEquals(90L, novemberResult.totalMinutes)
    }

    @Test
    fun partialWeeksStayInsideTheSelectedMonthAndSumToItsTotal() {
        val weeks = WorkingHours.monthWeeks(YearMonth.of(2026, 10))
        assertEquals(5, weeks.size)
        assertEquals(LocalDate.of(2026, 10, 1), weeks.first().from)
        assertEquals(LocalDate.of(2026, 10, 4), weeks.first().until)
        assertEquals(LocalDate.of(2026, 10, 31), weeks.last().until)
        val periods =
            listOf(
                period("sunday", "2026-10-04", "23:45", "03:00"),
                period("extra", "2026-10-11", "22:00", "01:00", WorkSource.EXTRA),
            )
        assertEquals(15L, WorkingHours.summarize(periods, weeks[0]).totalMinutes)
        assertEquals(300L, WorkingHours.summarize(periods, weeks[1]).totalMinutes)
        assertEquals(
            WorkingHours.summarize(periods, october).totalMinutes,
            weeks.sumOf { WorkingHours.summarize(periods, it).totalMinutes },
        )
    }

    @Test
    fun leapFebruaryAndSixWeekMonthsHaveCompleteNonOverlappingDays() {
        for (year in 2024..2028) for (month in 1..12) {
            val value = YearMonth.of(year, month)
            val days =
                WorkingHours.monthWeeks(value).flatMap { window ->
                    generateSequence(window.from) { it.plusDays(1) }
                        .takeWhile { it <= window.until }
                        .toList()
                }
            assertEquals(value.lengthOfMonth(), days.size)
            assertEquals(days.size, days.distinct().size)
            assertEquals(value.atDay(1), days.first())
            assertEquals(value.atEndOfMonth(), days.last())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDurationIsRejected() {
        period("zero", "2026-10-05", "12:00", "12:00")
    }

    @Test
    fun overlappingExtrasAndScheduledHoursAreCountedOnce() {
        val periods =
            listOf(
                period("work", "2026-10-05", "16:00", "18:00"),
                period("extra1", "2026-10-05", "13:00", "17:00", WorkSource.EXTRA),
                period("extra2", "2026-10-05", "15:00", "20:00", WorkSource.EXTRA),
            )
        val result = WorkingHours.summarize(periods.reversed(), october)
        assertEquals(120L, result.workMinutes)
        assertEquals(300L, result.extraMinutes)
        assertEquals(420L, result.totalMinutes)
        assertEquals(result, WorkingHours.summarize(periods, october))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicatePeriodIdsAreRejected() {
        val work = period("same", "2026-10-05", "12:00", "15:00")
        WorkingHours.summarize(listOf(work, work), october)
    }

    @Test
    fun explicitCalendarEndDatesPreserveWholeDaysAcrossMonths() {
        val work =
            WorkPeriod(
                "calendar",
                LocalDate.of(2026, 10, 31),
                LocalTime.NOON,
                LocalTime.NOON,
                WorkSource.SCHEDULE,
                LocalDate.of(2026, 11, 2),
            )
        assertEquals(720L, WorkingHours.summarize(listOf(work), october).totalMinutes)
        assertEquals(
            2160L,
            WorkingHours.summarize(
                    listOf(work),
                    HoursWindow(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
                )
                .totalMinutes,
        )
        assertThrows(IllegalArgumentException::class.java) { work.copy(endDate = work.date) }
    }
}
