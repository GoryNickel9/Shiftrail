package it.turni.app.calendar

import it.turni.app.data.ExtraWorkEntity
import it.turni.app.data.ShiftEntity
import it.turni.core.*
import java.time.LocalDate
import java.time.LocalTime

/** The stored shift remains the base schedule so editing/deleting an extra is reversible. */
fun baseCell(shift: ShiftEntity): ShiftCell =
    ShiftCell(
        LocalDate.parse(shift.date),
        "",
        CellType.valueOf(shift.type),
        shift.startTime?.let(LocalTime::parse),
        shift.endTime?.let(LocalTime::parse),
        shift.code,
        reviewed = true,
    )

fun extraHoursCell(shift: ShiftEntity, extras: List<ExtraWorkEntity>): ShiftCell =
    extraHoursCell(baseCell(shift), extras)

fun extraHoursCell(base: ShiftCell, extras: List<ExtraWorkEntity>): ShiftCell {
    val periods =
        extras.filter { it.date == base.date.toString() }.map { it.period() }.toMutableList()
    if (periods.isEmpty()) return base
    require(!base.requiresCalendarReview()) { "REVIEW_REQUIRED" }
    if (base.type == CellType.WORK_SHIFT)
        periods += WorkPeriod("base", base.date, base.start!!, base.end!!, WorkSource.SCHEDULE)
    val start = periods.minOf { it.startDateTime }
    val end = periods.maxOf { it.endDateTime }
    require(end < start.plusDays(1)) { "CALENDAR_EDIT_DURATION" }
    return base.copy(
        type = CellType.WORK_SHIFT,
        start = start.toLocalTime(),
        end = end.toLocalTime(),
        code = null,
    )
}

fun extraHoursSummary(shift: ShiftEntity, extras: List<ExtraWorkEntity>): ShiftEntity {
    val cell = extraHoursCell(shift, extras)
    return shift.copy(
        startTime = cell.start?.toString(),
        endTime = cell.end?.toString(),
        endNextDay = cell.endNextDay,
        type = cell.type.name,
        code = cell.code,
    )
}
