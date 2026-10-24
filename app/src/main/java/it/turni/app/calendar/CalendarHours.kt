package it.turni.app.calendar

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
        periods +=
            WorkPeriod(
                shift.id,
                start.toLocalDate(),
                start.toLocalTime(),
                end.toLocalTime(),
                WorkSource.SCHEDULE,
                end.toLocalDate(),
            )
    }
    return CalendarSchedule(summaries, periods)
}
