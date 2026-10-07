package it.turni.core

import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

data class EventOptions(
    val title: String,
    val location: String = "",
    val description: String = "",
    val reminderMinutes: Int? = null,
    val color: Int? = null,
)

data class CalendarEvent(
    val date: LocalDate,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val timezone: String,
    val title: String,
    val location: String,
    val description: String,
    val reminderMinutes: Int?,
    val color: Int?,
    val contentSignature: String,
)

object EventFactory {
    const val REST_EVENT_TITLE = "Giorno di riposo"

    /** Reject gaps and overlaps. Choosing a UTC offset silently would change the user's shift. */
    private fun instant(local: LocalDateTime, zone: ZoneId): Long {
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.size == 1) { "DST_AMBIGUOUS:$local" }
        return local.toInstant(offsets.single()).toEpochMilli()
    }

    // Keep the mappings parameter for source compatibility; rest is fixed and ignores old legends.
    @Suppress("UNUSED_PARAMETER")
    fun create(
        cell: ShiftCell,
        mappings: List<CodeMapping>,
        options: EventOptions,
        zone: ZoneId,
    ): CalendarEvent? {
        require(!cell.needsReview || cell.reviewed) { "REVIEW_REQUIRED" }
        if (cell.type == CellType.EMPTY) return null
        val allDay = cell.type == CellType.CODE && cell.code == ShiftParser.REST_CODE
        require(allDay || cell.type == CellType.WORK_SHIFT) { "UNKNOWN_SHIFT" }
        val start: Long
        val end: Long
        val signature: String
        val title: String
        if (allDay) {
            // CalendarContract requires UTC midnight and an exclusive end for all-day events.
            start = cell.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            end = cell.date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            title = REST_EVENT_TITLE
            signature = "${cell.signature()}|$title|ALL_DAY"
        } else {
            val startTime = cell.start
            val endTime = cell.end
            require(startTime != null && endTime != null && startTime != endTime) { "INVALID_TIME" }
            start = instant(cell.date.atTime(startTime), zone)
            end =
                instant(cell.date.plusDays(if (endTime < startTime) 1 else 0).atTime(endTime), zone)
            require(end > start) { "INVALID_DURATION" }
            // Preserve existing work-event signatures so upgrades do not cause needless updates.
            signature = "${cell.signature()}|$startTime|$endTime|null"
            title = options.title
        }
        return CalendarEvent(
            cell.date,
            start,
            end,
            allDay,
            if (allDay) "UTC" else zone.id,
            title,
            options.location,
            options.description,
            options.reminderMinutes,
            options.color,
            sha256(
                "$signature|$start|$end|$title|${options.location}|${options.description}|${options.reminderMinutes}|${options.color}"
            ),
        )
    }
}

fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(
        ""
    ) {
        "%02x".format(it)
    }

fun eventMarker(employeeId: String, date: LocalDate, calendarId: Long) =
    "[TURNI:${sha256("$employeeId|$date|$calendarId")} ]"

data class SavedEvent(
    val date: LocalDate,
    val signature: String,
    val eventId: Long,
    val calendarId: Long,
)

enum class ChangeKind {
    INSERT,
    UPDATE,
    UNCHANGED,
    MISSING,
    RECREATE,
}

data class SyncChange(
    val kind: ChangeKind,
    val date: LocalDate,
    val incoming: CalendarEvent?,
    val previous: SavedEvent?,
)

object SyncPlanner {
    /**
     * One event per employee/day/calendar in this MVP. Missing dates are never deleted
     * automatically.
     */
    fun plan(
        incoming: List<CalendarEvent>,
        saved: List<SavedEvent>,
        calendarId: Long,
        existingIds: Set<Long>,
    ): List<SyncChange> {
        require(incoming.map { it.date }.distinct().size == incoming.size) {
            "MULTIPLE_SHIFTS_PER_DAY"
        }
        val previous = saved.filter { it.calendarId == calendarId }.associateBy { it.date }
        val fresh = incoming.associateBy { it.date }
        return (fresh.keys + previous.keys).sorted().map { date ->
            val next = fresh[date]
            val old = previous[date]
            val kind =
                when {
                    next == null -> ChangeKind.MISSING
                    old == null -> ChangeKind.INSERT
                    old.eventId !in existingIds -> ChangeKind.RECREATE
                    old.signature == next.contentSignature -> ChangeKind.UNCHANGED
                    else -> ChangeKind.UPDATE
                }
            SyncChange(kind, date, next, old)
        }
    }
}
