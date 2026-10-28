package it.turni.app.calendar

import androidx.room.withTransaction
import it.turni.app.data.*
import it.turni.core.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
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

data class SyncCommitResult(val changed: Int, val added: Int)

class SyncRepository(private val db: TurniDatabase, private val calendar: CalendarRepository) {
    private val mutex = Mutex()

    suspend fun saveExtra(
        extra: ExtraWorkEntity,
        previous: ExtraWorkEntity?,
        calendarId: Long?,
        options: EventOptions,
        zone: ZoneId,
    ) = changeExtra(extra.employeeId, extra, previous, calendarId, options, zone)

    suspend fun removeExtra(
        extra: ExtraWorkEntity,
        options: EventOptions,
        zone: ZoneId,
    ) = changeExtra(extra.employeeId, null, extra, null, options, zone)

    private suspend fun changeExtra(
        employee: String,
        extra: ExtraWorkEntity?,
        previous: ExtraWorkEntity?,
        calendarId: Long?,
        options: EventOptions,
        zone: ZoneId,
    ) = mutex.withLock {
        require(previous == null || previous.employeeId == employee)
        val before = db.dao().allExtraWork(employee).first()
        require(previous == null || before.any { it == previous }) { "EXTRA_CHANGED" }
        extra?.let {
            it.period()
            require(previous == null || it.id == previous.id)
            require(previous != null || before.none { existing -> existing.id == it.id })
            val duplicate = db.dao().findExtra(employee, it.date, it.startTime, it.endTime)
            require(duplicate == null || duplicate.id == it.id) { "EXTRA_DUPLICATE" }
        }
        val after = before.filter { it.id != previous?.id } + listOfNotNull(extra)
        val shifts = db.dao().allRecordedShifts(employee).first()
        data class Pending(val shift: ShiftEntity, val event: CalendarEvent?, val isNew: Boolean)
        val pending = mutableListOf<Pending>()
        for (day in listOfNotNull(previous?.date, extra?.date).distinct()) {
            val date = LocalDate.parse(day)
            val old =
                shifts
                    .filter { it.date == day }
                    .maxWithOrNull(compareBy<ShiftEntity> { it.updatedAt }.thenBy { it.id })
            val remaining = after.filter { it.date == day }
            if (old == null && remaining.isEmpty()) continue
            val destination =
                old?.calendarId ?: requireNotNull(calendarId) { "CALENDAR_UNAVAILABLE" }
            val now = System.currentTimeMillis()
            val week = db.dao().weekFor(employee, date.with(java.time.DayOfWeek.MONDAY).toString())
            var base =
                old
                    ?: ShiftEntity(
                        sha256("$employee|$day|$destination"),
                        week?.id ?: sha256("$employee|${date.with(java.time.DayOfWeek.MONDAY)}"),
                        employee,
                        day,
                        null,
                        null,
                        false,
                        null,
                        CellType.EMPTY.name,
                        1.0,
                        -1,
                        destination,
                        "",
                        now,
                        now,
                    )
            if (old != null) {
                val marker = eventMarker(employee, date, destination)
                val provider =
                    calendar.event(old.calendarEventId)
                        ?: calendar.findMarker(destination, marker)?.let { calendar.event(it) }
                if (provider != null) {
                    // Never overwrite an unrelated event, or discard an external calendar edit.
                    val actual =
                        calendarSchedule(
                            listOf(old.copy(calendarEventId = provider.id)),
                            mapOf(provider.id to provider),
                            zone,
                        )
                    val expected =
                        EventFactory.create(extraHoursCell(old, before), emptyList(), options, zone)
                    val unexpanded = EventFactory.create(baseCell(old), emptyList(), options, zone)
                    val target =
                        EventFactory.create(extraHoursCell(old, after), emptyList(), options, zone)
                    fun matches(event: CalendarEvent?) =
                        event != null &&
                            event.startMillis == provider.start &&
                            event.endMillis == provider.end &&
                            event.allDay == provider.allDay
                    if (before.any { it.date == day }) {
                        require(matches(expected) || matches(unexpanded) || matches(target)) {
                            "CALENDAR_EXTRA_CONFLICT"
                        }
                    } else if (!matches(target)) {
                        val live = actual.shifts.single()
                        require(live.date == day) { "CALENDAR_EXTRA_CONFLICT" }
                        base = live
                    }
                    base = base.copy(calendarEventId = provider.id)
                }
            }
            val event = EventFactory.create(extraHoursCell(base, after), emptyList(), options, zone)
            pending += Pending(base, event, old == null)
        }
        val written = mutableMapOf<String, Long?>()
        for ((destination, changes) in pending.groupBy { it.shift.calendarId }) {
            val ids =
                calendar.apply(
                    destination,
                    changes.map {
                        val date = LocalDate.parse(it.shift.date)
                        CalendarMutation(
                            date,
                            it.event,
                            it.shift.calendarEventId.takeIf { id -> id >= 0 },
                            eventMarker(employee, date, destination),
                        )
                    },
                )
            changes.forEach { written[it.shift.id] = ids[LocalDate.parse(it.shift.date)] }
        }
        db.withTransaction {
            previous?.let { db.dao().removeExtra(it.id, employee) }
            extra?.let { db.dao().saveExtra(it) }
            for ((shift, event, isNew) in pending) {
                if (event == null && shift.type == CellType.EMPTY.name) {
                    db.dao().removeShift(shift.id)
                    continue
                }
                if (isNew) {
                    val monday = LocalDate.parse(shift.date).with(java.time.DayOfWeek.MONDAY)
                    if (db.dao().weekFor(employee, monday.toString()) == null)
                        db.dao()
                            .saveWeek(
                                WeekEntity(
                                    shift.weekId,
                                    employee,
                                    monday.toString(),
                                    shift.createdAt,
                                    "",
                                )
                            )
                }
                db.dao()
                    .saveShift(
                        shift.copy(
                            calendarEventId = written[shift.id] ?: shift.calendarEventId,
                            updatedAt = maxOf(System.currentTimeMillis(), shift.updatedAt + 1),
                        )
                    )
            }
        }
    }

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
        val extras = db.dao().allExtraWork(employee).first()
        val expanded =
            extraHoursCell(
                old.copy(
                    date = date.toString(),
                    startTime = start.toString(),
                    endTime = end.toString(),
                    type = CellType.WORK_SHIFT.name,
                    code = null,
                ),
                extras,
            )
        val event = requireNotNull(EventFactory.create(expanded, emptyList(), options, zone))
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
        options: EventOptions? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): PreparedSync {
        val saved =
            db.dao()
                .previous(employee, monday.toString(), monday.plusDays(6).toString(), calendarId)
        val extras = db.dao().allExtraWork(employee).first()
        val expandedEvents = cells.mapNotNull { cell ->
            val event = events.firstOrNull { it.date == cell.date }
            if (extras.none { it.date == cell.date.toString() }) event
            else
                EventFactory.create(
                    extraHoursCell(cell, extras),
                    emptyList(),
                    options
                        ?: if (event == null) EventOptions()
                        else
                            EventOptions(
                                location = event.location,
                                description = event.description,
                                reminderMinutes = event.reminderMinutes,
                                color = event.color,
                                restColor = event.color,
                            ),
                    if (event == null || event.allDay) zone else ZoneId.of(event.timezone),
                )
        }
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
                val incoming = expandedEvents.firstOrNull { it.date == cell.date }
                // Existing rest events are preserved; disabling rest never silently deletes them.
                if (
                    options?.saveRestDays == false &&
                        incoming == null &&
                        provider?.allDay == true &&
                        provider.title == EventFactory.REST_EVENT_TITLE
                )
                    continue
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
            } else if (
                old != null &&
                    !(options?.saveRestDays == false &&
                        old.code == ShiftParser.REST_CODE &&
                        old.type == CellType.CODE.name)
            )
                recovered += old.saved()
        }
        val changes = SyncPlanner.plan(expandedEvents, recovered, calendarId, existing)
        return PreparedSync(employee, monday, calendarId, hash, cells, changes, saved)
    }

    suspend fun commit(plan: PreparedSync, selected: Set<LocalDate>): Int =
        commitWithReceipt(plan, selected).changed

    suspend fun commitWithReceipt(plan: PreparedSync, selected: Set<LocalDate>) = mutex.withLock {
        val changes = plan.changes.filter { it.date in selected && it.kind != ChangeKind.UNCHANGED }
        val mutations = changes.map {
            CalendarMutation(
                it.date,
                it.incoming,
                it.previous?.eventId,
                eventMarker(plan.employee, it.date, plan.calendar),
            )
        }
        val receipt = calendar.applyWithReceipt(plan.calendar, mutations, requireVisible = true)
        val ids = receipt.ids
        calendar.verifyInstances(
            plan.calendar,
            plan.changes
                .filter { it.kind == ChangeKind.UNCHANGED }
                .associate {
                    requireNotNull(it.previous).eventId to requireNotNull(it.incoming)
                },
        )
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
        SyncCommitResult(changes.size, receipt.added)
    }
}
