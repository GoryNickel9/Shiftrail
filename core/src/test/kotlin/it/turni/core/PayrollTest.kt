package it.turni.core

import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class PayrollTest {
    @Test
    fun uncertainReadingsStillExposeCandidateDifferencesWithoutClaimingVerification() {
        val uncertain =
            row(2, 180, entry(12), exit(15)).copy(issues = setOf(PayrollReadIssue.CONFIDENCE))
        val result = compare(report(uncertain), period(2, "12:00", "15:30"))[1]
        assertEquals(
            setOf(PayrollDifference.READING, PayrollDifference.HOURS, PayrollDifference.TIMES),
            result.differences,
        )
        assertEquals(30L, result.deltaMinutes)
    }

    @Test
    fun ignoredAllowanceLabelsDoNotMakeValidClockAndTotlavDigitsUncertain() {
        val columns = (0..10).map { .1f + it * .03f }
        fun token(text: String, x: Float, confidence: Float) =
            OcrToken(text, Box(x - .01f, .2f, x + .01f, .21f), confidence)
        val row =
            PayrollParser.row(
                2,
                listOf(
                    token("2", .07f, .99f),
                    token("12:00", .115f, .99f),
                    token("15:00", .145f, .99f),
                    token("01:00", .48f, .1f),
                    token("M25", .54f, .1f),
                    token("03:00", .75f, .99f),
                    token("TOTLAV", .88f, .99f),
                ),
                Box(0f, 0f, 1f, 1f),
                columns,
            )
        assertFalse(row.issues.toString(), PayrollReadIssue.CONFIDENCE in row.issues)
        val badDigits =
            PayrollParser.row(
                2,
                listOf(
                    token("2", .07f, .99f),
                    token("03:00", .75f, .2f),
                    token("TOTLAV", .88f, .99f),
                ),
                Box(0f, 0f, 1f, 1f),
                columns,
            )
        assertTrue(PayrollReadIssue.CONFIDENCE in badDigits.issues)
    }

    @Test
    fun monthOcrZeroIsRecoveredOnlyInsideTheMonthVocabulary() {
        val tokens =
            listOf(
                OcrToken("0ttobre", Box(.1f, .1f, .2f, .12f), .9f),
                OcrToken("2026", Box(.3f, .1f, .4f, .12f), .99f),
            )
        assertEquals(YearMonth.of(2026, 10), PayrollParser.month(tokens))
        assertNull(
            PayrollParser.month(
                tokens.map { if (it.text == "0ttobre") it.copy(text = "0ttobrx") else it }
            )
        )
    }

    private val month = YearMonth.of(2026, 10)
    private val source = Box(0f, 0f, 1f, 1f)

    private fun row(day: Int, minutes: Int = 0, vararg punches: PayrollPunch) =
        PayrollDay(day, minutes, punches.toList(), "", source)

    private fun report(vararg changes: PayrollDay): PayrollReport {
        val days = (1..31).map { day -> changes.firstOrNull { it.day == day } ?: row(day) }
        return PayrollReport(month, "Test User", days.sumOf { it.minutes ?: 0 }, days, true)
    }

    private fun entry(hour: Int, minute: Int = 0) =
        PayrollPunch(PunchKind.ENTRY, hour * 60 + minute)

    private fun exit(hour: Int, minute: Int = 0) = PayrollPunch(PunchKind.EXIT, hour * 60 + minute)

    private fun period(
        day: Int,
        start: String,
        end: String,
        id: String = "base",
        source: WorkSource = WorkSource.SCHEDULE,
        breaks: List<UnpaidBreak> = emptyList(),
    ) =
        WorkPeriod(
            id,
            month.atDay(day),
            LocalTime.parse(start),
            LocalTime.parse(end),
            source,
            breaks = breaks,
        )

    private fun compare(report: PayrollReport, vararg periods: WorkPeriod) =
        PayrollComparator.compare(report, periods.toList()).days

    @Test
    fun overnightExitIsOwnedByEntryDateAndNotNextDaysShift() {
        val report = report(row(3, 300, entry(19)), row(4, 180, exit(0), entry(11), exit(14)))
        val result =
            compare(report, period(3, "19:00", "00:00"), period(4, "11:00", "14:00", "next"))
        assertTrue(result[2].differences.isEmpty())
        assertTrue(result[3].differences.isEmpty())
        assertEquals(300L, result[2].appMinutes)
        assertEquals(1, result[2].documentIntervals.size)
    }

    @Test
    fun netBreaksAndExtrasAreNotCountedTwice() {
        val report = report(row(2, 390, entry(10), exit(12), entry(12, 30), exit(17)))
        val pause = UnpaidBreak(month.atDay(2).atTime(12, 0), month.atDay(2).atTime(12, 30))
        val result =
            compare(
                report,
                period(2, "10:00", "16:00", breaks = listOf(pause)),
                period(2, "15:00", "17:00", "extra", WorkSource.EXTRA),
            )[1]
        assertEquals(390L, result.appMinutes)
        assertTrue(result.differences.toString(), result.differences.isEmpty())
    }

    @Test
    fun hoursAndClockTimesAreSeparateDifferences() {
        val report = report(row(1, 180, entry(18, 29), exit(21, 32)))
        val result = compare(report, period(1, "18:30", "21:30"))[0]
        assertEquals(setOf(PayrollDifference.TIMES), result.differences)
        assertEquals(0L, result.deltaMinutes)
    }

    @Test
    fun unequalHoursAreNeverRoundedAway() {
        val report = report(row(2, 180, entry(12), exit(15)))
        val result = compare(report, period(2, "12:00", "15:01"))[1]
        assertEquals(1L, result.deltaMinutes)
        assertTrue(PayrollDifference.HOURS in result.differences)
    }

    @Test
    fun missingScheduleIsNotZeroAndKnownRestIsComparable() {
        val report = report(row(2, 180, entry(12), exit(15)))
        val result = PayrollComparator.compare(report, emptyList(), setOf(month.atDay(1))).days
        assertEquals(0L, result[0].appMinutes)
        assertTrue(result[0].differences.isEmpty())
        assertNull(result[1].appMinutes)
        assertTrue(PayrollDifference.MISSING_APP in result[1].differences)
        assertFalse(PayrollDifference.HOURS in result[1].differences)
    }

    @Test
    fun unpairedEntryBlocksClockComparisonInsteadOfInventingExit() {
        val report = report(row(2, 180, entry(12)))
        val result = compare(report, period(2, "12:00", "15:00"))[1]
        assertTrue(PayrollDifference.INCOMPLETE_PUNCHES in result.differences)
        assertFalse(PayrollDifference.TIMES in result.differences)
    }

    @Test
    fun unpairedExitAndReplacementEntryFlagBothAffectedDates() {
        val report =
            report(row(1, 0, exit(6)), row(2, 180, entry(12)), row(3, 180, entry(12), exit(15)))
        val timeline = PayrollComparator.timeline(report)
        assertEquals(setOf(month.atDay(1), month.atDay(2), month.atDay(3)), timeline.incomplete)
        assertEquals(1, timeline.intervals[month.atDay(3)]!!.size)
    }

    @Test
    fun daylightSavingCodeDoesNotAddAnHour() {
        val special = row(24, 375, entry(22)).copy(daylightSaving = true)
        val report = report(special, row(25, 0, exit(4, 15)))
        val result = compare(report, period(24, "22:00", "04:15"))[23]
        assertEquals(375L, result.appMinutes)
        assertEquals(setOf(PayrollDifference.DAYLIGHT_SAVING), result.differences)
        val reviewed =
            report.copy(
                days =
                    report.days.map {
                        if (it.day == 24) it.copy(daylightSavingReviewed = true) else it
                    }
            )
        assertTrue(compare(reviewed, period(24, "22:00", "04:15"))[23].differences.isEmpty())
    }

    @Test
    fun monthEndOwnsTheCompleteShiftButMissingNextMonthExitStaysIncomplete() {
        val report = report(row(31, 180, entry(22)))
        val result = compare(report, period(31, "22:00", "01:00"))[30]
        assertEquals(180L, result.appMinutes)
        assertTrue(PayrollDifference.INCOMPLETE_PUNCHES in result.differences)
        assertFalse(PayrollDifference.HOURS in result.differences)
    }

    @Test
    fun unavailableCalendarAndInvalidBreakPreventAllClear() {
        val report = report(row(2, 180, entry(12), exit(15)))
        val invalid = UnpaidBreak(month.atDay(2).atTime(17, 0), month.atDay(2).atTime(17, 30))
        val result =
            PayrollComparator.compare(
                    report,
                    listOf(period(2, "12:00", "15:00", breaks = listOf(invalid))),
                    calendarAvailable = false,
                )
                .days[1]
        assertTrue(PayrollDifference.APP_REVIEW in result.differences)
        assertEquals(180L, result.appMinutes)
    }

    @Test
    fun readingIssuesRequireManualConfirmationEvenWhenTotalsCoincide() {
        val uncertain =
            row(2, 180, entry(12), exit(15)).copy(issues = setOf(PayrollReadIssue.CONFIDENCE))
        assertTrue(
            PayrollDifference.READING in
                compare(report(uncertain), period(2, "12:00", "15:00"))[1].differences
        )
        assertTrue(
            compare(report(uncertain.copy(reviewed = true)), period(2, "12:00", "15:00"))[1]
                .differences
                .isEmpty()
        )
    }

    @Test
    fun totalsCannotBeCertifiedWithMissingAmountsOrWorkBeyondMonth() {
        val valid = report(row(2, 180, entry(12), exit(15)))
        assertTrue(valid.totalMatches)
        assertFalse(valid.copy(printedMinutes = 181).totalMatches)
        assertFalse(
            valid
                .copy(days = valid.days.map { if (it.day == 2) it.copy(minutes = null) else it })
                .totalMatches
        )
        assertTrue(valid.copy(month = YearMonth.of(2026, 2)).totalMatches)
        assertFalse(
            valid
                .copy(
                    month = YearMonth.of(2026, 2),
                    days = valid.days.map { if (it.day == 31) it.copy(minutes = 180) else it },
                )
                .totalMatches
        )
    }

    @Test
    fun manualDurationsAreMinutesAndRejectInvalidValues() {
        assertEquals(6915, PayrollParser.duration("115:15"))
        assertEquals(1440, PayrollParser.duration("24:00", 1440))
        assertNull(PayrollParser.duration("3.50"))
        assertNull(PayrollParser.duration("03:60"))
        assertNull(PayrollParser.duration("-03:00"))
        assertNull(PayrollParser.duration("745:00"))
        assertEquals(
            listOf(exit(0, 3), entry(11), exit(14)),
            PayrollParser.punches("U 00:03; E 11:00; U 14:00"),
        )
        assertNull(PayrollParser.punches("E 25:00"))
        assertNull(PayrollParser.punches("E 12:00; ignored"))
    }

    @Test
    fun nameMatchingAcceptsReversedOrderButNotAnotherEmployee() {
        val profile = EmployeeProfile("Anna Rossi", listOf("Anna Maria Rossi"))
        assertTrue(PayrollParser.matchesName("ROSSI ANNA", profile))
        assertTrue(PayrollParser.matchesName("Rossi Anna Maria", profile))
        assertFalse(PayrollParser.matchesName("Rossi Andrea", profile))
        assertFalse(PayrollParser.matchesName(null, profile))
    }

    @Test
    fun rowParserUsesOnlyTotlavAndPreservesMidnightColumnKinds() {
        val columns = (0..10).map { .1f + it * .03f }
        fun token(text: String, x: Float, width: Float = .02f) =
            OcrToken(text, Box(x - .01f, .2f, x - .01f + width, .21f), .99f)
        val tokens =
            listOf(
                token("4", .07f),
                token("00:03", .145f),
                token("11:00", .175f),
                token("14:00", .205f),
                token("03:00", .48f),
                token("M20", .54f),
                token("05:00", .60f),
                token("MORE+", .67f),
                token("03:00", .75f),
                token("HH_LAV", .81f),
                token("03:00", .88f),
                token("TOTLAV", .95f),
            )
        val result = PayrollParser.row(4, tokens, source, columns)
        assertEquals(180, result.minutes)
        assertEquals(listOf(exit(0, 3), entry(11), exit(14)), result.punches)
        assertTrue(result.issues.toString(), result.issues.isEmpty())
    }

    @Test
    fun malformedTotalsAndClockDigitsAreNotSilentlyDiscarded() {
        val columns = (0..10).map { .1f + it * .03f }
        val tokens =
            listOf(
                OcrToken("2", Box(.05f, .2f, .07f, .21f), .99f),
                OcrToken("25:99", Box(.11f, .2f, .13f, .21f), .99f),
                OcrToken("HH_LAV", Box(.5f, .2f, .6f, .21f), .99f),
            )
        val result = PayrollParser.row(2, tokens, source, columns)
        assertNull(result.minutes)
        assertTrue(PayrollReadIssue.TOTAL in result.issues)
        assertTrue(PayrollReadIssue.TIME in result.issues)
    }
}
