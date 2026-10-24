package it.turni.app

import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.calendar.CalendarMutation
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
    fun anExistingEventCanOpenWhileHiddenThenReappearsWithoutReimporting() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CalendarFixture("hidden-existing").use { fixture ->
            val provider = CalendarRepository(context.contentResolver)
            val date = LocalDate.of(2026, 11, 9)
            val event =
                requireNotNull(
                    EventFactory.create(
                        ShiftParser.parse("09-17", date),
                        emptyList(),
                        EventOptions(),
                        ZoneId.of("Europe/Rome"),
                    )
                )
            val id =
                requireNotNull(
                    provider
                        .apply(
                            fixture.id,
                            listOf(CalendarMutation(date, event, null, "hidden-existing-marker")),
                        )[date]
                )
            context.contentResolver.update(
                ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, fixture.id),
                ContentValues().apply { put(CalendarContract.Calendars.VISIBLE, 0) },
                null,
                null,
            )
            assertEquals(id, provider.event(id)!!.id)
            assertFalse(provider.calendars().single { it.id == fixture.id }.visible)
            try {
                provider.verifyInstances(fixture.id, mapOf(id to event))
                fail("A hidden event must be absent from the visible agenda")
            } catch (error: IllegalStateException) {
                assertEquals("CALENDAR_WRITE_FAILED", error.message)
            }
            provider.enableForImport(fixture.id)
            provider.verifyInstances(fixture.id, mapOf(id to event))
            assertEquals(id, provider.event(id)!!.id)
        }
    }

    @Test
    fun activationRejectsReadOnlyCalendarsWithoutChangingVisibility() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CalendarFixture("read-only-enable").use { fixture ->
            val uri =
                ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, fixture.id)
                    .buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(
                        CalendarContract.Calendars.ACCOUNT_NAME,
                        "read-only-enable",
                    )
                    .appendQueryParameter(
                        CalendarContract.Calendars.ACCOUNT_TYPE,
                        CalendarContract.ACCOUNT_TYPE_LOCAL,
                    )
                    .build()
            context.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(
                        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                        CalendarContract.Calendars.CAL_ACCESS_READ,
                    )
                    put(CalendarContract.Calendars.VISIBLE, 0)
                    put(CalendarContract.Calendars.SYNC_EVENTS, 0)
                },
                null,
                null,
            )
            val provider = CalendarRepository(context.contentResolver)
            try {
                provider.enableForImport(fixture.id)
                fail("Read-only calendars cannot be enabled for an import")
            } catch (error: IllegalArgumentException) {
                assertEquals("CALENDAR_NOT_WRITABLE", error.message)
            }
            val calendar = provider.calendars().single { it.id == fixture.id }
            assertFalse(calendar.writable)
            assertFalse(calendar.visible)
            assertFalse(calendar.syncEnabled)
        }
    }

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
                assertFalse(provider.calendars().single { it.id == fixture.id }.syncEnabled)
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
                resolver.update(
                    calendarUri,
                    ContentValues().apply { put(CalendarContract.Calendars.VISIBLE, 0) },
                    null,
                    null,
                )
                assertFalse(provider.calendars().single { it.id == fixture.id }.visible)
                enabled(1)
                try {
                    sync.commit(plan, cells.map { it.date }.toSet())
                    fail("A calendar hidden after the preview must fail before writing events")
                } catch (error: IllegalArgumentException) {
                    assertEquals("CALENDAR_SYNC_DISABLED", error.message)
                }
                assertNull(
                    provider.findMarker(
                        fixture.id,
                        it.turni.core.eventMarker("employee", monday, fixture.id),
                    )
                )
                CalendarFixture("keep-disabled").use { other ->
                    val otherUri =
                        ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, other.id)
                    resolver.update(
                        otherUri,
                        ContentValues().apply {
                            put(CalendarContract.Calendars.VISIBLE, 0)
                            put(CalendarContract.Calendars.SYNC_EVENTS, 0)
                        },
                        null,
                        null,
                    )
                    provider.enableForImport(fixture.id)
                    val calendars = provider.calendars()
                    assertTrue(calendars.single { it.id == fixture.id }.visible)
                    assertTrue(calendars.single { it.id == fixture.id }.syncEnabled)
                    assertFalse(calendars.single { it.id == other.id }.visible)
                    assertFalse(calendars.single { it.id == other.id }.syncEnabled)
                }
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
