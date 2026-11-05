package it.turni.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

enum class PunchKind {
    ENTRY,
    EXIT,
}

data class PayrollPunch(
    val kind: PunchKind,
    val minute: Int,
    val previousWorkDay: Boolean = false,
) {
    init {
        require(minute in 0..1440) { "PAYROLL_INVALID_TIME" }
        require(!previousWorkDay || kind == PunchKind.ENTRY) { "PAYROLL_INVALID_TIME" }
    }
}

enum class PayrollReadIssue {
    DAY,
    TOTAL,
    TIME,
    CONFIDENCE,
}

data class PayrollDay(
    val day: Int,
    val minutes: Int?,
    val punches: List<PayrollPunch>,
    val raw: String,
    val source: Box,
    val issues: Set<PayrollReadIssue> = emptySet(),
    val reviewed: Boolean = false,
    val daylightSaving: Boolean = false,
    val daylightSavingReviewed: Boolean = false,
) {
    init {
        require(day in 1..31)
        require(minutes == null || minutes in 0..1440) { "PAYROLL_INVALID_DURATION" }
        require(punches.size <= 10 && raw.length <= 4096)
        require(source.left >= 0 && source.top >= 0 && source.right <= 1 && source.bottom <= 1)
    }

    val needsReview
        get() = minutes == null || (!reviewed && issues.isNotEmpty())
}

data class PayrollReport(
    val month: YearMonth,
    val employeeName: String?,
    val printedMinutes: Int?,
    val days: List<PayrollDay>,
    val identityConfirmed: Boolean = false,
) {
    init {
        require(month.year in 1900..2100)
        require(days.size in 28..31 && days.map { it.day } == (1..days.size).toList())
        require(printedMinutes == null || printedMinutes in 0..44640)
        require(employeeName == null || employeeName.length <= 200)
    }

    val activeDays
        get() = days.filter { it.day <= month.lengthOfMonth() }

    val readMinutes
        get() = activeDays.sumOf { it.minutes ?: 0 }

    val complete
        get() = activeDays.size == month.lengthOfMonth() && activeDays.none { it.minutes == null }

    val outsideMonth
        get() = days.any {
            it.day > month.lengthOfMonth() && ((it.minutes ?: 0) > 0 || it.punches.isNotEmpty())
        }

    val totalMatches
        get() = complete && !outsideMonth && printedMinutes != null && readMinutes == printedMinutes
}

data class PayrollInterval(val start: LocalDateTime, val end: LocalDateTime) {
    init {
        require(end > start)
    }
}

data class PayrollTimeline(
    val intervals: Map<LocalDate, List<PayrollInterval>>,
    val incomplete: Set<LocalDate>,
    val ambiguous: Set<LocalDate> = emptySet(),
)

enum class PayrollDifference {
    HOURS,
    TIMES,
    MISSING_APP,
    READING,
    INCOMPLETE_PUNCHES,
    DAYLIGHT_SAVING,
    APP_REVIEW,
    WORK_DAY,
}

data class PayrollComparisonDay(
    val date: LocalDate,
    val document: PayrollDay,
    val appMinutes: Long?,
    val documentIntervals: List<PayrollInterval>,
    val appIntervals: List<PayrollInterval>,
    val appPeriods: List<WorkPeriod>,
    val differences: Set<PayrollDifference>,
) {
    val deltaMinutes
        get() = appMinutes?.let { app -> document.minutes?.let { app - it } }
}

data class PayrollComparison(val days: List<PayrollComparisonDay>) {
    val appMinutes
        get() = days.sumOf { it.appMinutes ?: 0 }

    val documentMinutes
        get() = days.sumOf { it.document.minutes ?: 0 }

    val differenceDays
        get() = days.count {
            PayrollDifference.HOURS in it.differences || PayrollDifference.TIMES in it.differences
        }

    val pendingDays
        get() = days.count {
            it.differences.any { issue ->
                issue !in setOf(PayrollDifference.HOURS, PayrollDifference.TIMES)
            }
        }
}

object PayrollComparator {
    /** A payroll work day owns the complete shift, including its exit on the next date. */
    fun timeline(report: PayrollReport): PayrollTimeline {
        val intervals = mutableMapOf<LocalDate, MutableList<PayrollInterval>>()
        val incomplete = mutableSetOf<LocalDate>()
        val ambiguous = mutableSetOf<LocalDate>()
        var entry: Pair<LocalDateTime, LocalDate>? = null
        for (row in report.activeDays) {
            val date = report.month.atDay(row.day)
            val entries = row.punches.count { it.kind == PunchKind.ENTRY }
            if (
                !row.reviewed &&
                    entry?.first?.toLocalDate()?.isBefore(date) == true &&
                    (entries > 1 || (entries > 0 && row.minutes == 0))
            ) {
                ambiguous += date
                ambiguous += requireNotNull(entry).second
            }
            for (punch in row.punches) {
                val at = date.atStartOfDay().plusMinutes(punch.minute.toLong())
                when (punch.kind) {
                    PunchKind.ENTRY -> {
                        entry?.let {
                            incomplete += it.second
                            incomplete += date
                        }
                        val owner = if (punch.previousWorkDay) date.minusDays(1) else date
                        if (owner < report.month.atDay(1)) incomplete += date
                        entry = at to owner
                    }
                    PunchKind.EXIT -> {
                        val start = entry
                        if (start == null) incomplete += date
                        else if (
                            at <= start.first ||
                                Duration.between(start.first, at).toMinutes() >= 1440
                        ) {
                            incomplete += start.second
                            incomplete += date
                        } else
                            intervals.getOrPut(start.second) { mutableListOf() } +=
                                PayrollInterval(start.first, at)
                        entry = null
                    }
                }
            }
        }
        entry?.let { incomplete += it.second }
        return PayrollTimeline(intervals, incomplete, ambiguous)
    }

    /** Full, deduplicated net durations are grouped by start date, never by the PDF exit row. */
    fun compare(
        report: PayrollReport,
        periods: List<WorkPeriod>,
        knownRestDates: Set<LocalDate> = emptySet(),
        calendarAvailable: Boolean = true,
    ): PayrollComparison {
        val from = report.month.atDay(1)
        val until = report.month.atEndOfMonth()
        val relevant = periods.filter {
            it.startDateTime < until.plusDays(2).atStartOfDay() &&
                it.endDateTime > from.minusDays(1).atStartOfDay()
        }
        val summary =
            WorkingHours.summarize(relevant, HoursWindow(from.minusDays(1), until.plusDays(1)))
        val byDate = summary.entries.groupBy { it.period.date }
        val timeline = timeline(report)
        return PayrollComparison(
            report.activeDays.map { day ->
                val date = report.month.atDay(day.day)
                val entries = byDate[date].orEmpty()
                val appPeriods = entries.map { it.period }
                val app =
                    if (entries.isNotEmpty() || date in knownRestDates) entries.sumOf { it.minutes }
                    else null
                val appIntervals =
                    merge(
                        appPeriods.flatMap {
                            it.paidIntervals().map { (start, end) -> PayrollInterval(start, end) }
                        }
                    )
                val documentIntervals = merge(timeline.intervals[date].orEmpty())
                val differences = buildSet {
                    if (day.needsReview) add(PayrollDifference.READING)
                    if (date in timeline.ambiguous) add(PayrollDifference.WORK_DAY)
                    if (app == null) add(PayrollDifference.MISSING_APP)
                    if (
                        date in timeline.incomplete ||
                            (day.minutes != null && day.minutes > 0 && documentIntervals.isEmpty())
                    )
                        add(PayrollDifference.INCOMPLETE_PUNCHES)
                    if (day.daylightSaving && !day.daylightSavingReviewed)
                        add(PayrollDifference.DAYLIGHT_SAVING)
                    if (
                        !calendarAvailable ||
                            appPeriods.any {
                                it.validBreaks.size != it.breaks.size ||
                                    Duration.between(it.startDateTime, it.endDateTime)
                                        .toMinutes() >= 1440
                            }
                    )
                        add(PayrollDifference.APP_REVIEW)
                    if (app != null && day.minutes != null && app != day.minutes.toLong())
                        add(PayrollDifference.HOURS)
                    if (
                        app != null &&
                            PayrollDifference.INCOMPLETE_PUNCHES !in this &&
                            PayrollDifference.WORK_DAY !in this &&
                            appIntervals != documentIntervals
                    )
                        add(PayrollDifference.TIMES)
                }
                PayrollComparisonDay(
                    date,
                    day,
                    app,
                    documentIntervals,
                    appIntervals,
                    appPeriods,
                    differences,
                )
            }
        )
    }

    private fun merge(intervals: List<PayrollInterval>): List<PayrollInterval> {
        val result = mutableListOf<PayrollInterval>()
        for (value in intervals.sortedBy { it.start }) {
            val previous = result.lastOrNull()
            if (previous != null && value.start <= previous.end)
                result[result.lastIndex] =
                    PayrollInterval(previous.start, maxOf(previous.end, value.end))
            else result += value
        }
        return result
    }
}
