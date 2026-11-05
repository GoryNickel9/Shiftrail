package it.turni.core

import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class PayrollContinuationTest {
    @Test
    fun aRestartAfterOvernightExitIsReviewedBeforeAssigningItToAWorkDay() {
        val month = YearMonth.of(2026, 10)
        val days =
            (1..31).map { day ->
                val punches =
                    when (day) {
                        24 -> listOf(PayrollPunch(PunchKind.ENTRY, 1320))
                        25 ->
                            listOf(
                                PayrollPunch(PunchKind.EXIT, 120),
                                PayrollPunch(PunchKind.ENTRY, 150),
                                PayrollPunch(PunchKind.EXIT, 240),
                                PayrollPunch(PunchKind.ENTRY, 1200),
                            )
                        26 -> listOf(PayrollPunch(PunchKind.EXIT, 0))
                        else -> emptyList()
                    }
                PayrollDay(
                    day,
                    if (day == 24) 330 else if (day == 25) 240 else 0,
                    punches,
                    "",
                    Box(0f, 0f, 1f, 1f),
                )
            }
        val report = PayrollReport(month, null, 570, days, true)
        val pause = UnpaidBreak(month.atDay(25).atTime(2, 0), month.atDay(25).atTime(2, 30))
        val periods =
            listOf(
                WorkPeriod(
                    "night",
                    month.atDay(24),
                    LocalTime.of(22, 0),
                    LocalTime.of(4, 0),
                    WorkSource.SCHEDULE,
                    breaks = listOf(pause),
                ),
                WorkPeriod(
                    "next",
                    month.atDay(25),
                    LocalTime.of(20, 0),
                    LocalTime.MIDNIGHT,
                    WorkSource.SCHEDULE,
                ),
            )
        val initial = PayrollComparator.compare(report, periods)
        assertTrue(PayrollDifference.WORK_DAY in initial.days[23].differences)
        assertTrue(PayrollDifference.WORK_DAY in initial.days[24].differences)
        assertFalse(PayrollDifference.TIMES in initial.days[23].differences)
        val confirmed =
            report.copy(
                days =
                    days.map { row ->
                        if (row.day == 25)
                            row.copy(
                                reviewed = true,
                                punches =
                                    row.punches.map {
                                        if (it.kind == PunchKind.ENTRY && it.minute == 150)
                                            it.copy(previousWorkDay = true)
                                        else it
                                    },
                            )
                        else row
                    }
            )
        val result = PayrollComparator.compare(confirmed, periods)
        assertTrue(result.days[23].differences.toString(), result.days[23].differences.isEmpty())
        assertTrue(result.days[24].differences.toString(), result.days[24].differences.isEmpty())
        assertEquals(330L, result.days[23].appMinutes)
        assertEquals(2, result.days[23].documentIntervals.size)
    }
}
