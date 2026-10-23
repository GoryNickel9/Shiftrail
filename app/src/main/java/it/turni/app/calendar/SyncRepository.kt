package it.turni.app.calendar

import androidx.room.withTransaction
import it.turni.app.data.*
import it.turni.core.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PreparedSync(
    val employee: String,
    val monday: LocalDate,
    val calendar: Long,
    val hash: String,
    val cells: List<ShiftCell>,
    val changes: List<SyncChange>,
    val saved: List<ShiftEntity>,
)

class SyncRepository(private val db: TurniDatabase, private val calendar: CalendarRepository) {
    private val mutex = Mutex()

    suspend fun updateHours(
        employee: String,
        shiftId: String,
        start: LocalTime,
        end: LocalTime,
        options: EventOptions,
        zone: ZoneId,
    ) = mutex.withLock {
        require(start != end) { "INVALID_DURATION" }
        val old = requireNotNull(db.dao().shift(shiftId, employee))
        val originalDate = LocalDate.parse(old.date)
        val marker = eventMarker(employee, originalDate, old.calendarId)
        val direct = calendar.event(old.calendarEventId)
        if (direct != null)
            require(direct.calendarId == old.calendarId && direct.description.contains(marker)) {
                "CALENDAR_OWNERSHIP_CHANGED"
            }
        val eventId = direct?.id ?: calendar.findMarker(old.calendarId, marker)
        val provider = direct ?: eventId?.let { calendar.event(it) }
        require(
            old.type == CellType.WORK_SHIFT.name ||
                (old.type == CellType.CODE.name && provider?.allDay == false)
        )
        provider?.let {
            val providerStart =
                java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDateTime()
            val providerEnd = java.time.Instant.ofEpochMilli(it.end).atZone(zone).toLocalDateTime()
            require(providerEnd < providerStart.plusDays(1)) { "CALENDAR_EDIT_DURATION" }
        }
        val date =
            provider?.let { java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }
                ?: originalDate
        val cell = ShiftParser.parse("$start-$end", date).copy(reviewed = true)
        val event = requireNotNull(EventFactory.create(cell, emptyList(), options, zone))
        val ids =
            calendar.apply(old.calendarId, listOf(CalendarMutation(date, event, eventId, marker)))
        // Keep the imported week, source hash and shift ID when editing a single day.
        db.dao()
            .saveShift(
                old.copy(
                    startTime = start.toString(),
                    endTime = end.toString(),
                    endNextDay = end < start,
                    type = CellType.WORK_SHIFT.name,
                    code = null,
                    calendarEventId = requireNotNull(ids[date]),
                    contentSignature = event.contentSignature,
                    updatedAt = maxOf(System.currentTimeMillis(), old.updatedAt + 1),
                )
            )
    }

    suspend fun prepare(
        employee: String,
        monday: LocalDate,
        calendarId: Long,
        hash: String,
        cells: List<ShiftCell>,
        events: List<CalendarEvent>,
    ): PreparedSync {
        val saved =
            db.dao()
                .previous(employee, monday.toString(), monday.plusDays(6).toString(), calendarId)
        val existing = mutableSetOf<Long>()
        val recovered = mutableListOf<SavedEvent>()
        for (cell in cells) {
            val old = saved.firstOrNull { it.date == cell.date.toString() }
            val marker = eventMarker(employee, cell.date, calendarId)
            val direct =
                old?.let { calendar.event(it.calendarEventId) }
                    ?.takeIf { it.calendarId == calendarId && it.description.contains(marker) }
            val id = direct?.id ?: calendar.findMarker(calendarId, marker)
            if (id != null) {
                existing += id
                val provider = calendar.event(id)
                val incoming = events.firstOrNull { it.date == cell.date }
                // If Room was not committed, force an update of the recovered event to canonical
                // values.
                val unchanged =
                    provider != null &&
                        incoming != null &&
                        provider.start == incoming.startMillis &&
                        provider.end == incoming.endMillis &&
                        provider.title == incoming.title &&
                        provider.location == incoming.location &&
                        provider.allDay == incoming.allDay &&
                        provider.description == "${incoming.description}\n$marker"
                recovered +=
                    SavedEvent(
                        cell.date,
                        if (old != null && direct != null && unchanged) old.contentSignature
                        else "RECOVERED",
                        id,
                        calendarId,
                    )
            } else if (old != null) recovered += old.saved()
        }
        val changes = SyncPlanner.plan(events, recovered, calendarId, existing)
        return PreparedSync(employee, monday, calendarId, hash, cells, changes, saved)
    }

    suspend fun commit(plan: PreparedSync, selected: Set<LocalDate>) = mutex.withLock {
        val changes = plan.changes.filter { it.date in selected && it.kind != ChangeKind.UNCHANGED }
        val mutations = changes.map {
            CalendarMutation(
                it.date,
                it.incoming,
                it.previous?.eventId,
                eventMarker(plan.employee, it.date, plan.calendar),
            )
        }
        val ids = calendar.apply(plan.calendar, mutations)
        val now = System.currentTimeMillis()
        val weekId = sha256("${plan.employee}|${plan.monday}")
        db.withTransaction {
            db.dao()
                .saveWeek(WeekEntity(weekId, plan.employee, plan.monday.toString(), now, plan.hash))
            for (change in plan.changes) {
                val incoming = change.incoming
                if (change.kind != ChangeKind.UNCHANGED && change.date !in selected) continue
                val oldEntity = plan.saved.firstOrNull { it.date == change.date.toString() }
                val id =
                    if (change.kind == ChangeKind.UNCHANGED) change.previous?.eventId
                    else ids[change.date]
                if (incoming == null) {
                    oldEntity?.let { db.dao().removeShift(it.id) }
                } else if (id != null) {
                    val cell = plan.cells.first { it.date == change.date }
                    db.dao()
                        .saveShift(
                            ShiftEntity(
                                sha256("${plan.employee}|${cell.date}|${plan.calendar}"),
                                weekId,
                                plan.employee,
                                cell.date.toString(),
                                cell.start?.toString(),
                                cell.end?.toString(),
                                cell.endNextDay,
                                cell.code,
                                cell.type.name,
                                cell.confidence,
                                id,
                                plan.calendar,
                                incoming.contentSignature,
                                oldEntity?.createdAt ?: now,
                                now,
                            )
                        )
                }
            }
        }
        changes.size
    }
}
