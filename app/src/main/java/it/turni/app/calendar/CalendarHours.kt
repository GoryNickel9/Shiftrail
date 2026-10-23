package it.turni.app.calendar

import it.turni.app.data.ShiftEntity
import it.turni.core.*
import java.time.Instant
import java.time.ZoneId

/** Only linked, marked events contribute calendar hours; personal events remain excluded. */
fun calendarWorkPeriods(
    shifts: List<ShiftEntity>,
    events: Map<Long, ProviderEvent>,
    zone: ZoneId,
): List<WorkPeriod> = shifts.mapNotNull { shift ->
    val event = events[shift.calendarEventId] ?: return@mapNotNull null
    if (
        event.calendarId != shift.calendarId ||
            !event.description.contains(
                eventMarker(
                    shift.employeeId,
                    java.time.LocalDate.parse(shift.date),
                    shift.calendarId,
                )
            )
    )
        throw IllegalArgumentException("CALENDAR_OWNERSHIP_CHANGED")
    if (event.allDay) return@mapNotNull null
    val start = Instant.ofEpochMilli(event.start).atZone(zone).toLocalDateTime()
    val end = Instant.ofEpochMilli(event.end).atZone(zone).toLocalDateTime()
    require(end > start) { "INVALID_CALENDAR_DURATION" }
    WorkPeriod(
        shift.id,
        start.toLocalDate(),
        start.toLocalTime(),
        end.toLocalTime(),
        WorkSource.SCHEDULE,
        end.toLocalDate(),
    )
}
