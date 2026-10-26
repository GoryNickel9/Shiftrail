package it.turni.app.calendar

import it.turni.app.data.ExtraWorkEntity
import it.turni.app.data.ShiftEntity
import it.turni.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class CalendarSchedule(val shifts: List<ShiftEntity>, val periods: List<WorkPeriod>)

/** Only linked, marked events contribute calendar hours; personal events remain excluded. */
fun calendarWorkPeriods(
    shifts: List<ShiftEntity>,
    events: Map<Long, ProviderEvent>,
    zone: ZoneId,
): List<WorkPeriod> = calendarSchedule(shifts, events, zone).periods

fun calendarSchedule(
    shifts: List<ShiftEntity>,
    events: Map<Long, ProviderEvent>,
    zone: ZoneId,
    extras: List<ExtraWorkEntity> = emptyList(),
): CalendarSchedule {
    val summaries = mutableListOf<ShiftEntity>()
    val periods = mutableListOf<WorkPeriod>()
    for (shift in shifts) {
        val event = events[shift.calendarEventId] ?: continue
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
        if (event.allDay) {
            summaries +=
                shift.copy(
                    date =
                        Instant.ofEpochMilli(event.start)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toString(),
                    startTime = null,
                    endTime = null,
                    endNextDay = false,
                    type = CellType.CODE.name,
                    code = "X",
                )
            continue
        }
        val start = Instant.ofEpochMilli(event.start).atZone(zone).toLocalDateTime()
        val end = Instant.ofEpochMilli(event.end).atZone(zone).toLocalDateTime()
        require(end > start) { "INVALID_CALENDAR_DURATION" }
        summaries +=
            shift.copy(
                date = start.toLocalDate().toString(),
                startTime = start.toLocalTime().toString(),
                endTime = end.toLocalTime().toString(),
                endNextDay = end.toLocalDate() > start.toLocalDate(),
                type = CellType.WORK_SHIFT.name,
                code = null,
            )
        // An expanded event is a display envelope; its extra hours are counted separately.
        val expanded = extraHoursCell(shift, extras)
        val matchesExtras =
            extras.any { it.date == shift.date } &&
                expanded.date.atTime(expanded.start) == start &&
                expanded.endDate.atTime(expanded.end) == end
        if (matchesExtras && shift.type != CellType.WORK_SHIFT.name) continue
        val periodStart =
            if (matchesExtras) baseCell(shift).let { it.date.atTime(it.start) } else start
        val periodEnd =
            if (matchesExtras) baseCell(shift).let { it.endDate.atTime(it.end) } else end
        periods +=
            WorkPeriod(
                shift.id,
                periodStart.toLocalDate(),
                periodStart.toLocalTime(),
                periodEnd.toLocalTime(),
                WorkSource.SCHEDULE,
                periodEnd.toLocalDate(),
            )
    }
    return CalendarSchedule(summaries, periods)
}
