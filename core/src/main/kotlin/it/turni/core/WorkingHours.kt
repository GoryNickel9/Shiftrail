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

/** Scheduled clock time, not an attendance record. Intervals contain no breaks. */
data class WorkPeriod(
    val id: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val source: WorkSource,
    val endDate: LocalDate? = null,
) {
    init {
        require(endDateTime > startDateTime) { "INVALID_DURATION" }
    }

    val startDateTime: LocalDateTime
        get() = date.atTime(start)

    val endDateTime: LocalDateTime
        get() = (endDate ?: date.plusDays(if (end < start) 1 else 0)).atTime(end)
}

data class HoursEntry(val period: WorkPeriod, val minutes: Long)

data class HoursSummary(val entries: List<HoursEntry>) {
    val workMinutes
        get() = entries.filter { it.period.source == WorkSource.SCHEDULE }.sumOf { it.minutes }

    val extraMinutes
        get() = entries.filter { it.period.source == WorkSource.EXTRA }.sumOf { it.minutes }

    val totalMinutes
        get() = workMinutes + extraMinutes
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
        return HoursSummary(
            periods
                .mapNotNull { period ->
                    val start = maxOf(from, period.startDateTime)
                    val end = minOf(until, period.endDateTime)
                    if (end <= start) null
                    else HoursEntry(period, Duration.between(start, end).toMinutes())
                }
                .sortedWith(compareBy({ it.period.startDateTime }, { it.period.id }))
        )
    }
}
