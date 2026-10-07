package it.turni.app.calendar

import androidx.room.withTransaction
import it.turni.app.data.*
import it.turni.core.*
import java.time.LocalDate
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
