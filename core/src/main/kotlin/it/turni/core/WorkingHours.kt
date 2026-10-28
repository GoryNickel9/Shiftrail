package it.turni.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

enum class WorkSource {
    SCHEDULE,
    EXTRA,
}

data class UnpaidBreak(val start: LocalDateTime, val end: LocalDateTime) {
    init {
        require(end > start) { "BREAK_INVALID" }
    }
}

/** Scheduled clock time, not an attendance record. Breaks do not alter calendar bounds. */
data class WorkPeriod(
    val id: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val source: WorkSource,
    val endDate: LocalDate? = null,
    val breaks: List<UnpaidBreak> = emptyList(),
) {
    init {
        require(endDateTime > startDateTime) { "INVALID_DURATION" }
    }

    val startDateTime: LocalDateTime
        get() = date.atTime(start)

    val endDateTime: LocalDateTime
        get() = (endDate ?: date.plusDays(if (end < start) 1 else 0)).atTime(end)

    val validBreaks: List<UnpaidBreak>
        get() = breaks.filter { it.start >= startDateTime && it.end <= endDateTime }

    fun validateBreaks() {
        require(validBreaks.size == breaks.size) { "BREAK_OUTSIDE_SHIFT" }
        require(breaks.sortedBy { it.start }.zipWithNext().all { (a, b) -> a.end <= b.start }) {
            "BREAK_OVERLAP"
        }
    }

    fun paidIntervals(): List<Pair<LocalDateTime, LocalDateTime>> {
        val result = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        var cursor = startDateTime
        // Calendar edits can invalidate a saved break: retain it for review, without subtracting
        // it.
        for (pause in validBreaks.sortedBy { it.start }) {
            if (pause.start > cursor) result += cursor to pause.start
            cursor = maxOf(cursor, pause.end)
        }
        if (cursor < endDateTime) result += cursor to endDateTime
        return result
    }
}

data class HoursEntry(val period: WorkPeriod, val minutes: Long, val nightMinutes: Long = 0)

data class HoursSummary(val entries: List<HoursEntry>) {
    val workMinutes
        get() = entries.filter { it.period.source == WorkSource.SCHEDULE }.sumOf { it.minutes }

    val extraMinutes
        get() = entries.filter { it.period.source == WorkSource.EXTRA }.sumOf { it.minutes }

    val totalMinutes
        get() = workMinutes + extraMinutes

    /** Subset of totalMinutes, including extras; never added to the total again. */
    val nightMinutes
        get() = entries.sumOf { it.nightMinutes }
}

data class HoursWindow(val from: LocalDate, val until: LocalDate) {
    init {
        require(!until.isBefore(from))
    }
}

object WorkingHours {
    fun monthWeeks(month: YearMonth): List<HoursWindow> {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        return generateSequence(first.with(DayOfWeek.MONDAY)) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(last) }
            .map { HoursWindow(maxOf(it, first), minOf(it.plusDays(6), last)) }
            .toList()
    }

    /** Half-open intervals prevent double counting at midnight and month/week boundaries. */
    fun summarize(periods: List<WorkPeriod>, window: HoursWindow): HoursSummary {
        val from = window.from.atStartOfDay()
        val until = window.until.plusDays(1).atStartOfDay()
        require(periods.map { it.id }.distinct().size == periods.size) { "DUPLICATE_WORK_PERIOD" }
        val occupied = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        return HoursSummary(
            periods
                .sortedWith(compareBy({ it.source }, { it.startDateTime }, { it.id }))
                .mapNotNull { period ->
                    val start = maxOf(from, period.startDateTime)
                    val end = minOf(until, period.endDateTime)
                    if (end <= start) null
                    else {
                        var uncovered = Duration.ZERO
                        var nightMinutes = 0L
                        val paid =
                            period
                                .paidIntervals()
                                .map { maxOf(start, it.first) to minOf(end, it.second) }
                                .filter { it.second > it.first }
                        for ((paidStart, paidEnd) in paid) {
                            var cursor = paidStart
                            for ((usedStart, usedEnd) in occupied.sortedBy { it.first }) {
                                if (usedEnd <= cursor || usedStart >= paidEnd) continue
                                if (usedStart > cursor) {
                                    uncovered += Duration.between(cursor, usedStart)
                                    nightMinutes += nightMinutesBetween(cursor, usedStart)
                                }
                                cursor = maxOf(cursor, minOf(usedEnd, paidEnd))
                            }
                            uncovered += Duration.between(cursor, paidEnd)
                            nightMinutes += nightMinutesBetween(cursor, paidEnd)
                        }
                        occupied += paid
                        HoursEntry(period, uncovered.toMinutes(), nightMinutes)
                    }
                }
                .sortedWith(compareBy({ it.period.startDateTime }, { it.period.id }))
        )
    }

    private fun nightMinutesBetween(start: LocalDateTime, end: LocalDateTime): Long {
        var date = start.toLocalDate()
        var minutes = 0L
        while (date.atStartOfDay() < end) {
            val from = maxOf(start, date.atStartOfDay())
            val until = minOf(end, date.atTime(6, 0))
            if (until > from) minutes += Duration.between(from, until).toMinutes()
            date = date.plusDays(1)
        }
        return minutes
    }
}
