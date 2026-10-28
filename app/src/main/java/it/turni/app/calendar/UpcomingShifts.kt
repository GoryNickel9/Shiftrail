package it.turni.app.calendar

import it.turni.app.data.ShiftEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

fun ShiftEntity.workStart(): LocalDateTime? =
    if (type != "WORK_SHIFT" || startTime == null || endTime == null) null
    else runCatching { LocalDate.parse(date).atTime(LocalTime.parse(startTime)) }.getOrNull()

fun upcomingWorkShifts(shifts: List<ShiftEntity>, now: LocalDateTime): List<ShiftEntity> =
    shifts
        .mapNotNull { shift -> shift.workStart()?.takeIf { it > now }?.let { it to shift } }
        .sortedWith(compareBy({ it.first }, { it.second.id }))
        .map { it.second }
