package it.turni.core

import java.time.ZoneId

data class WorkReminder(val key: String, val start: Long, val end: Long, val trigger: Long)

/** One reminder per work day, using the same shift + extras envelope as the calendar. */
fun workReminders(periods: List<WorkPeriod>, minutes: Int, zone: ZoneId): List<WorkReminder> {
    require(minutes in 0..10080) { "INVALID_NOTIFICATION_MINUTES" }
    return periods
        .groupBy { it.date }
        .values
        .map { day ->
            val start = day.minOf { it.startDateTime }.atZone(zone).toInstant().toEpochMilli()
            val end = day.maxOf { it.endDateTime }.atZone(zone).toInstant().toEpochMilli()
            WorkReminder("$start-$minutes", start, end, start - minutes * 60_000L)
        }
        .sortedBy { it.trigger }
}
