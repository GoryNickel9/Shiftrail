package it.turni.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val MAX_NOTIFICATION_MINUTES = 24 * 60
const val NOTIFICATION_MINUTE_STEP = 5

fun validNotificationMinutes(value: Int) =
    value in 0..MAX_NOTIFICATION_MINUTES && value % NOTIFICATION_MINUTE_STEP == 0

fun normalizeNotificationMinutes(value: Int) =
    value.coerceIn(0, MAX_NOTIFICATION_MINUTES) / NOTIFICATION_MINUTE_STEP *
        NOTIFICATION_MINUTE_STEP

data class WorkReminder(val key: String, val start: Long, val end: Long, val trigger: Long)

fun workTimeRange(start: Long, end: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val format = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    return "${format.format(Instant.ofEpochMilli(start))} – ${format.format(Instant.ofEpochMilli(end))}"
}

/** One reminder per work day, using the same shift + extras envelope as the calendar. */
fun workReminders(periods: List<WorkPeriod>, minutes: Int, zone: ZoneId): List<WorkReminder> {
    require(validNotificationMinutes(minutes)) { "INVALID_NOTIFICATION_MINUTES" }
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
