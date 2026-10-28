package it.turni.app.calendar

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import it.turni.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

data class WritableCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val localOnly: Boolean = false,
    val visible: Boolean = true,
    val syncEnabled: Boolean = true,
    val writable: Boolean = true,
    val accountType: String = "",
)

data class ProviderEvent(
    val id: Long,
    val calendarId: Long,
    val description: String,
    val start: Long,
    val end: Long,
    val title: String,
    val location: String,
    val allDay: Boolean,
)

fun preferredCalendar(calendars: List<WritableCalendar>, id: Long?): WritableCalendar? =
    calendars.singleOrNull {
        it.id == id && it.writable && it.visible && it.syncEnabled
    }

data class CalendarMutation(
    val date: java.time.LocalDate,
    val event: CalendarEvent?,
    val oldId: Long?,
    val marker: String,
)

data class CalendarWriteResult(val ids: Map<java.time.LocalDate, Long?>, val added: Int)

class CalendarRepository(private val resolver: ContentResolver) {
    fun changes() = callbackFlow {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }

                override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                    trySend(Unit)
                }
            }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
        .conflate()

    suspend fun calendars(): List<WritableCalendar> =
        withContext(Dispatchers.IO) {
            resolver
                .query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(
                        CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Calendars.ACCOUNT_NAME,
                        CalendarContract.Calendars.NAME,
                        CalendarContract.Calendars.ACCOUNT_TYPE,
                        CalendarContract.Calendars.VISIBLE,
                        CalendarContract.Calendars.SYNC_EVENTS,
                        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    ),
                    null,
                    null,
                    null,
                )
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val displayName = cursor.getString(1).orEmpty()
                            val internalName = cursor.getString(3).orEmpty()
                            add(
                                WritableCalendar(
                                    cursor.getLong(0),
                                    displayName.ifBlank { internalName },
                                    cursor.getString(2).orEmpty(),
                                    cursor.getString(4) == CalendarContract.ACCOUNT_TYPE_LOCAL,
                                    cursor.getInt(5) == 1,
                                    cursor.getInt(6) == 1,
                                    cursor.getInt(7) >=
                                        CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                                    accountType = cursor.getString(4).orEmpty(),
                                )
                            )
                        }
                    }
                }
                .orEmpty()
                .sortedWith(
                    compareBy<WritableCalendar>(
                        { !it.writable },
                        { it.localOnly },
                        { it.name },
                        { it.id },
                    )
                )
        }

    suspend fun enableForImport(calendarId: Long) =
        withContext(Dispatchers.IO) {
            val calendar = calendars().singleOrNull { it.id == calendarId }
            require(calendar?.writable == true) { "CALENDAR_NOT_WRITABLE" }
            val count =
                resolver.update(
                    ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId),
                    ContentValues().apply {
                        put(CalendarContract.Calendars.VISIBLE, 1)
                        put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                    },
                    null,
                    null,
                )
            val enabled = calendars().singleOrNull { it.id == calendarId }
            check(count == 1 && enabled?.visible == true && enabled.syncEnabled) {
                "CALENDAR_ENABLE_FAILED"
            }
        }

    suspend fun event(id: Long): ProviderEvent? = events(listOf(id))[id]

    suspend fun events(ids: List<Long>): Map<Long, ProviderEvent> =
        withContext(Dispatchers.IO) {
            buildMap {
                for (chunk in ids.distinct().chunked(400)) resolver
                    .query(
                        CalendarContract.Events.CONTENT_URI,
                        arrayOf(
                            CalendarContract.Events._ID,
                            CalendarContract.Events.CALENDAR_ID,
                            CalendarContract.Events.DESCRIPTION,
                            CalendarContract.Events.DTSTART,
                            CalendarContract.Events.DTEND,
                            CalendarContract.Events.TITLE,
                            CalendarContract.Events.EVENT_LOCATION,
                            CalendarContract.Events.ALL_DAY,
                        ),
                        "${CalendarContract.Events.DELETED} = 0 AND ${CalendarContract.Events._ID} IN (${chunk.joinToString { "?" }})",
                        chunk.map(Long::toString).toTypedArray(),
                        null,
                    )
                    ?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val event =
                                ProviderEvent(
                                    cursor.getLong(0),
                                    cursor.getLong(1),
                                    cursor.getString(2).orEmpty(),
                                    cursor.getLong(3),
                                    cursor.getLong(4),
                                    cursor.getString(5).orEmpty(),
                                    cursor.getString(6).orEmpty(),
                                    cursor.getInt(7) == 1,
                                )
                            put(event.id, event)
                        }
                    }
            }
        }

    suspend fun findMarker(calendar: Long, marker: String): Long? =
        withContext(Dispatchers.IO) {
            resolver
                .query(
                    CalendarContract.Events.CONTENT_URI,
                    arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DESCRIPTION),
                    "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0 AND ${CalendarContract.Events.DESCRIPTION} LIKE ?",
                    arrayOf(calendar.toString(), "%$marker%"),
                    null,
                )
                ?.use { cursor ->
                    val ids = buildList {
                        while (cursor.moveToNext()) if (
                            cursor.getString(1).orEmpty().contains(marker)
                        )
                            add(cursor.getLong(0))
                    }
                    require(ids.size <= 1) { "MULTIPLE_CALENDAR_MATCHES" }
                    ids.singleOrNull()
                }
        }

    /**
     * Provider batch is atomic. Room is a separate store; markers recover an interrupted commit.
     */
    suspend fun apply(
        calendar: Long,
        mutations: List<CalendarMutation>,
        requireVisible: Boolean = false,
    ): Map<java.time.LocalDate, Long?> = applyWithReceipt(calendar, mutations, requireVisible).ids

    suspend fun applyWithReceipt(
        calendar: Long,
        mutations: List<CalendarMutation>,
        requireVisible: Boolean = false,
    ): CalendarWriteResult =
        withContext(Dispatchers.IO) {
            // A linked calendar can be renamed or hidden after the user imports a shift.
            val visible =
                resolver
                    .query(
                        ContentUris.withAppendedId(
                            CalendarContract.Calendars.CONTENT_URI,
                            calendar,
                        ),
                        arrayOf(
                            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                            CalendarContract.Calendars.SYNC_EVENTS,
                            CalendarContract.Calendars.VISIBLE,
                        ),
                        null,
                        null,
                        null,
                    )
                    ?.use { cursor ->
                        require(
                            cursor.moveToFirst() &&
                                cursor.getInt(0) >=
                                    CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                        ) {
                            "CALENDAR_NOT_WRITABLE"
                        }
                        require(cursor.getInt(1) == 1) { "CALENDAR_SYNC_DISABLED" }
                        require(!requireVisible || cursor.getInt(2) == 1) {
                            "CALENDAR_SYNC_DISABLED"
                        }
                        cursor.getInt(2) == 1
                    } ?: throw IllegalArgumentException("CALENDAR_NOT_WRITABLE")
            val operations = arrayListOf<ContentProviderOperation>()
            val indices = mutableMapOf<java.time.LocalDate, Int>()
            val previousIds = mutableMapOf<java.time.LocalDate, Long>()
            for (mutation in mutations) {
                // A previous batch may have succeeded before verification or Room failed.
                val old =
                    mutation.oldId?.let { event(it) }
                        ?: findMarker(calendar, mutation.marker)?.let { event(it) }
                old?.let { previousIds[mutation.date] = it.id }
                if (old != null)
                    require(
                        old.calendarId == calendar && old.description.contains(mutation.marker)
                    ) {
                        "CALENDAR_OWNERSHIP_CHANGED"
                    }
                indices[mutation.date] = operations.size
                val incoming = mutation.event
                if (incoming == null) {
                    if (old != null)
                        operations +=
                            ContentProviderOperation.newDelete(
                                    ContentUris.withAppendedId(
                                        CalendarContract.Events.CONTENT_URI,
                                        old.id,
                                    )
                                )
                                .build()
                    else indices.remove(mutation.date)
                    continue
                }
                val values =
                    ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calendar)
                        put(CalendarContract.Events.DTSTART, incoming.startMillis)
                        put(CalendarContract.Events.DTEND, incoming.endMillis)
                        put(CalendarContract.Events.EVENT_TIMEZONE, incoming.timezone)
                        put(CalendarContract.Events.ALL_DAY, if (incoming.allDay) 1 else 0)
                        put(CalendarContract.Events.TITLE, incoming.title)
                        put(CalendarContract.Events.EVENT_LOCATION, incoming.location)
                        put(
                            CalendarContract.Events.DESCRIPTION,
                            "${incoming.description}\n${mutation.marker}",
                        )
                        put(
                            CalendarContract.Events.HAS_ALARM,
                            if (incoming.reminderMinutes != null) 1 else 0,
                        )
                        if (incoming.color == null) putNull(CalendarContract.Events.EVENT_COLOR)
                        else put(CalendarContract.Events.EVENT_COLOR, incoming.color)
                    }
                val eventIndex = operations.size
                operations +=
                    if (old == null)
                        ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                            .withValues(values)
                            .build()
                    else
                        ContentProviderOperation.newUpdate(
                                ContentUris.withAppendedId(
                                    CalendarContract.Events.CONTENT_URI,
                                    old.id,
                                )
                            )
                            .withValues(values)
                            .withExpectedCount(1)
                            .build()
                if (old != null)
                    operations +=
                        ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI)
                            .withSelection(
                                "${CalendarContract.Reminders.EVENT_ID} = ?",
                                arrayOf(old.id.toString()),
                            )
                            .build()
                incoming.reminderMinutes?.let { minutes ->
                    val reminder =
                        ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                            .withValue(CalendarContract.Reminders.MINUTES, minutes)
                            .withValue(
                                CalendarContract.Reminders.METHOD,
                                CalendarContract.Reminders.METHOD_ALERT,
                            )
                    if (old == null)
                        reminder.withValueBackReference(
                            CalendarContract.Reminders.EVENT_ID,
                            eventIndex,
                        )
                    else reminder.withValue(CalendarContract.Reminders.EVENT_ID, old.id)
                    operations += reminder.build()
                }
            }
            val results =
                if (operations.isEmpty()) emptyArray()
                else resolver.applyBatch(CalendarContract.AUTHORITY, operations)
            val ids = mutations.associate { mutation ->
                mutation.date to
                    when {
                        mutation.event == null -> null
                        results.getOrNull(indices[mutation.date] ?: -1)?.uri != null ->
                            ContentUris.parseId(results[indices.getValue(mutation.date)].uri!!)
                        else -> previousIds[mutation.date]
                    }
            }
            val written = mutations.filter { it.event != null }
            val stored = events(ids.values.filterNotNull())
            for (mutation in written) {
                val incoming = requireNotNull(mutation.event)
                val actual = stored[ids[mutation.date]]
                check(
                    actual != null &&
                        actual.calendarId == calendar &&
                        actual.start == incoming.startMillis &&
                        actual.end == incoming.endMillis &&
                        actual.allDay == incoming.allDay &&
                        actual.title == incoming.title &&
                        actual.location == incoming.location &&
                        actual.description.contains(mutation.marker)
                ) {
                    "CALENDAR_WRITE_FAILED"
                }
            }
            if (visible && written.isNotEmpty()) {
                verifyInstances(
                    calendar,
                    written.associate {
                        requireNotNull(ids[it.date]) to requireNotNull(it.event)
                    },
                )
            }
            CalendarWriteResult(
                ids,
                mutations.count { mutation ->
                    mutation.event != null &&
                        results.getOrNull(indices[mutation.date] ?: -1)?.uri != null
                },
            )
        }

    suspend fun verifyInstances(calendar: Long, expected: Map<Long, CalendarEvent>) =
        withContext(Dispatchers.IO) {
            if (expected.isEmpty()) return@withContext
            // Events rows alone do not prove that the calendar can display the imported week.
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(uri, expected.values.minOf { it.startMillis })
            ContentUris.appendId(uri, expected.values.maxOf { it.endMillis })
            val instances =
                resolver
                    .query(
                        uri.build(),
                        arrayOf(CalendarContract.Instances.EVENT_ID),
                        "${CalendarContract.Instances.CALENDAR_ID} = ? AND ${CalendarContract.Instances.VISIBLE} = 1",
                        arrayOf(calendar.toString()),
                        null,
                    )
                    ?.use { cursor ->
                        buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) }
                    }
                    .orEmpty()
            check(expected.keys.all { it in instances }) { "CALENDAR_WRITE_FAILED" }
        }
}
