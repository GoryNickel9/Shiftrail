package it.turni.app

import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.calendar.CalendarRepository
import it.turni.app.calendar.SyncRepository
import it.turni.app.data.TurniDatabase
import it.turni.core.EventFactory
import it.turni.core.EventOptions
import it.turni.core.ShiftParser
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CalendarVisibilityTest {
    @Test
    fun disabledCalendarCannotSaveAWeekAndEnabledCalendarHasSevenVisibleInstances() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CalendarFixture("visibility-test").use { fixture ->
            val resolver = context.contentResolver
            val provider = CalendarRepository(resolver)
            val db = Room.inMemoryDatabaseBuilder(context, TurniDatabase::class.java).build()
            try {
                val sync = SyncRepository(db, provider)
                val monday = LocalDate.of(2026, 11, 2)
                val zone = ZoneId.of("Europe/Rome")
                val cells = (0L..6L).map { ShiftParser.parse("09-17", monday.plusDays(it)) }
                val events = cells.map {
                    requireNotNull(EventFactory.create(it, emptyList(), EventOptions(), zone))
                }
                val plan = sync.prepare("employee", monday, fixture.id, "week", cells, events)
                val calendarUri =
                    ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, fixture.id)
                fun enabled(value: Int) {
                    assertEquals(
                        1,
                        resolver.update(
                            calendarUri,
                            ContentValues().apply {
                                put(CalendarContract.Calendars.SYNC_EVENTS, value)
                            },
                            null,
                            null,
                        ),
                    )
                }
                enabled(0)
                assertFalse(provider.calendars().any { it.id == fixture.id })
                try {
                    sync.commit(plan, cells.map { it.date }.toSet())
                    fail("A disabled destination must fail before saving local shifts")
                } catch (error: IllegalArgumentException) {
                    assertEquals("CALENDAR_SYNC_DISABLED", error.message)
                }
                assertTrue(
                    db.dao()
                        .previous(
                            "employee",
                            monday.toString(),
                            monday.plusDays(6).toString(),
                            fixture.id,
                        )
                        .isEmpty()
                )
                assertNull(
                    provider.findMarker(
                        fixture.id,
                        it.turni.core.eventMarker("employee", monday, fixture.id),
                    )
                )
                enabled(1)
                assertTrue(provider.calendars().any { it.id == fixture.id })
                assertTrue(provider.calendars().single { it.id == fixture.id }.localOnly)
                assertEquals(7, sync.commit(plan, cells.map { it.date }.toSet()))
                // Retrying the same preview after an interrupted save must reuse provider IDs.
                assertEquals(7, sync.commit(plan, cells.map { it.date }.toSet()))
                val saved =
                    db.dao()
                        .previous(
                            "employee",
                            monday.toString(),
                            monday.plusDays(6).toString(),
                            fixture.id,
                        )
                assertEquals(7, saved.size)
                val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(
                    instancesUri,
                    monday.atStartOfDay(zone).toInstant().toEpochMilli(),
                )
                ContentUris.appendId(
                    instancesUri,
                    monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli(),
                )
                resolver
                    .query(
                        instancesUri.build(),
                        arrayOf(CalendarContract.Instances.EVENT_ID),
                        "${CalendarContract.Instances.CALENDAR_ID} = ?",
                        arrayOf(fixture.id.toString()),
                        null,
                    )!!
                    .use { cursor ->
                        val ids = buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) }
                        assertEquals(saved.map { it.calendarEventId }.toSet(), ids)
                    }
                val repeated = sync.prepare("employee", monday, fixture.id, "week", cells, events)
                assertEquals(0, sync.commit(repeated, emptySet()))
                val removed = saved.first()
                resolver.delete(
                    ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_URI,
                        removed.calendarEventId,
                    ),
                    null,
                    null,
                )
                try {
                    sync.commit(repeated, emptySet())
                    fail("An unchanged preview must not report success for a missing event")
                } catch (error: IllegalStateException) {
                    assertEquals("CALENDAR_WRITE_FAILED", error.message)
                }
                val repair = sync.prepare("employee", monday, fixture.id, "week", cells, events)
                assertEquals(1, sync.commit(repair, setOf(LocalDate.parse(removed.date))))
            } finally {
                db.close()
            }
        }
    }
}
