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

data class WritableCalendar(val id: Long, val name: String, val account: String)

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

data class CalendarMutation(
    val date: java.time.LocalDate,
    val event: CalendarEvent?,
    val oldId: Long?,
    val marker: String,
)

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
                    ),
                    "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
                    arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                    null,
                )
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val displayName = cursor.getString(1).orEmpty()
                            val internalName = cursor.getString(3).orEmpty()
                            if (
                                listOf(displayName, internalName).any {
                                    it.trim().equals("PC Sync", ignoreCase = true)
                                }
                            )
                                add(
                                    WritableCalendar(
                                        cursor.getLong(0),
                                        "Google Calendar",
                                        cursor.getString(2).orEmpty(),
                                    )
                                )
                        }
                    }
                }
                .orEmpty()
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
    ): Map<java.time.LocalDate, Long?> =
        withContext(Dispatchers.IO) {
            // A linked calendar can be renamed or hidden after the user imports a shift.
            val writable =
                resolver
                    .query(
                        ContentUris.withAppendedId(
                            CalendarContract.Calendars.CONTENT_URI,
                            calendar,
                        ),
                        arrayOf(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
                        null,
                        null,
                        null,
                    )
                    ?.use { cursor ->
                        cursor.moveToFirst() &&
                            cursor.getInt(0) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    } ?: false
            require(writable) { "CALENDAR_NOT_WRITABLE" }
            val operations = arrayListOf<ContentProviderOperation>()
            val indices = mutableMapOf<java.time.LocalDate, Int>()
            for (mutation in mutations) {
                val old = mutation.oldId?.let { event(it) }
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
            mutations.associate { mutation ->
                mutation.date to
                    when {
                        mutation.event == null -> null
                        results.getOrNull(indices[mutation.date] ?: -1)?.uri != null ->
                            ContentUris.parseId(results[indices.getValue(mutation.date)].uri!!)
                        else -> mutation.oldId
                    }
            }
        }
}
