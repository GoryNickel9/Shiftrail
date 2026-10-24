package it.turni.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.calendar.*
import it.turni.app.data.TurniDatabase
import it.turni.core.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarIntegrationTest {
    @Test
    fun providerInsertReimportUpdateRecoveryAndExplicitDelete() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.READ_CALENDAR,
        )
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.WRITE_CALENDAR,
        )
        val resolver = context.contentResolver
        val calendarUri =
            CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "turni-test")
                .appendQueryParameter(
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.ACCOUNT_TYPE_LOCAL,
                )
                .build()
        val values =
            ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, "turni-test")
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, "work-calendar")
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Turni test")
                put(CalendarContract.Calendars.CALENDAR_COLOR, 0xff006c67.toInt())
                put(
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    CalendarContract.Calendars.CAL_ACCESS_OWNER,
                )
                put(CalendarContract.Calendars.OWNER_ACCOUNT, "turni-test")
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "Europe/Rome")
                put(CalendarContract.Calendars.ALLOWED_REMINDERS, "1")
            }
        val id = ContentUris.parseId(requireNotNull(resolver.insert(calendarUri, values)))
        val otherId =
            ContentUris.parseId(
                requireNotNull(
                    resolver.insert(
                        calendarUri,
                        ContentValues(values).apply {
                            put(CalendarContract.Calendars.NAME, "other-calendar")
                            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Other calendar")
                        },
                    )
                )
            )
        val readOnlyId =
            ContentUris.parseId(
                requireNotNull(
                    resolver.insert(
                        calendarUri,
                        ContentValues(values).apply {
                            put(CalendarContract.Calendars.NAME, "read-only")
                            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "PC Sync")
                            put(
                                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                                CalendarContract.Calendars.CAL_ACCESS_READ,
                            )
                        },
                    )
                )
            )
        val db = Room.inMemoryDatabaseBuilder(context, TurniDatabase::class.java).build()
        try {
            val provider = CalendarRepository(resolver)
            val sync = SyncRepository(db, provider)
            val monday = LocalDate.of(2026, 10, 5)
            fun cells(raw: String) =
                (0L..6L).map { ShiftParser.parse(if (it == 0L) raw else "", monday.plusDays(it)) }
            fun events(raw: String, options: EventOptions = EventOptions(reminderMinutes = 15)) =
                listOf(
                    requireNotNull(
                        EventFactory.create(
                            cells(raw).first(),
                            emptyList(),
                            options,
                            ZoneId.of("Europe/Rome"),
                        )
                    )
                )
            val calendars = provider.calendars()
            assertEquals("Turni test", calendars.single { it.id == id }.name)
            assertEquals("turni-test", calendars.single { it.id == id }.account)
            assertEquals("Other calendar", calendars.single { it.id == otherId }.name)
            assertFalse(calendars.single { it.id == readOnlyId }.writable)
            resolver.update(
                calendarUri,
                ContentValues().apply {
                    put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "")
                },
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(otherId.toString()),
            )
            assertEquals("other-calendar", provider.calendars().single { it.id == otherId }.name)
            resolver
                .query(
                    ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id),
                    arrayOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )!!
                .use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Turni test", it.getString(0))
                }
            try {
                provider.apply(readOnlyId, emptyList())
                fail("Read-only calendars must not be writable through Shiftrail")
            } catch (error: IllegalArgumentException) {
                assertEquals("CALENDAR_NOT_WRITABLE", error.message)
            }
            assertTrue(provider.apply(otherId, emptyList()).isEmpty())
            fun assertOptions(event: Long, minutes: Int?, color: Int?) {
                resolver
                    .query(
                        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event),
                        arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.EVENT_COLOR),
                        null,
                        null,
                        null,
                    )!!
                    .use {
                        assertTrue(it.moveToFirst())
                        if (color == null) assertTrue(it.isNull(1))
                        else assertEquals(color.toInt(), it.getInt(1))
                    }
                resolver
                    .query(
                        CalendarContract.Reminders.CONTENT_URI,
                        arrayOf(CalendarContract.Reminders.MINUTES),
                        "${CalendarContract.Reminders.EVENT_ID} = ?",
                        arrayOf(event.toString()),
                        null,
                    )!!
                    .use {
                        assertEquals(if (minutes == null) 0 else 1, it.count)
                        if (minutes != null) {
                            assertTrue(it.moveToFirst())
                            assertEquals(minutes.toInt(), it.getInt(0))
                        }
                    }
            }
            val first =
                sync.prepare("employee", monday, id, "hash", cells("19-01"), events("19-01"))
            assertEquals(ChangeKind.INSERT, first.changes.single().kind)
            sync.commit(first, setOf(monday))
            val saved =
                db.dao()
                    .previous("employee", monday.toString(), monday.plusDays(6).toString(), id)
                    .single()
            val eventId = saved.calendarEventId
            assertEquals(21600000L, provider.event(eventId)!!.let { it.end - it.start })
            assertEquals("Lavoro", provider.event(eventId)!!.title)
            assertOptions(eventId, 15, 0xff1a73e8.toInt())
            assertTrue(
                db.dao()
                    .recordedShifts("employee", monday.toString(), monday.toString())
                    .first()
                    .single()
                    .endNextDay
            )
            val repeated =
                sync.prepare("employee", monday, id, "hash", cells("19-01"), events("19-01"))
            assertEquals(ChangeKind.UNCHANGED, repeated.changes.single().kind)
            sync.commit(repeated, emptySet())
            val changed =
                sync.prepare("employee", monday, id, "hash2", cells("18:30-01"), events("18:30-01"))
            assertEquals(ChangeKind.UPDATE, changed.changes.single().kind)
            sync.commit(changed, setOf(monday))
            assertEquals(
                eventId,
                db.dao()
                    .previous("employee", monday.toString(), monday.plusDays(6).toString(), id)
                    .single()
                    .calendarEventId,
            )
            assertEquals(23400000L, provider.event(eventId)!!.let { it.end - it.start })
            // Simulate provider committed, Room interrupted: recover by marker without inserting.
            db.dao().removeShift(saved.id)
            val recovered =
                sync.prepare("employee", monday, id, "hash2", cells("18:30-01"), events("18:30-01"))
            assertEquals(ChangeKind.UPDATE, recovered.changes.single().kind)
            sync.commit(recovered, setOf(monday))
            assertEquals(
                eventId,
                db.dao()
                    .previous("employee", monday.toString(), monday.plusDays(6).toString(), id)
                    .single()
                    .calendarEventId,
            )
            val rest = sync.prepare("employee", monday, id, "rest", cells("X"), events("X"))
            assertEquals(ChangeKind.UPDATE, rest.changes.single().kind)
            sync.commit(rest, setOf(monday))
            val restEvent = provider.event(eventId)!!
            assertEquals("Giorno di riposo", restEvent.title)
            assertTrue(restEvent.allDay)
            assertEquals(
                monday.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
                restEvent.start,
            )
            assertEquals(86400000L, restEvent.end - restEvent.start)
            assertOptions(eventId, 15, 0xff0b8043.toInt())
            val sameRest = sync.prepare("employee", monday, id, "rest", cells("X"), events("X"))
            assertEquals(ChangeKind.UNCHANGED, sameRest.changes.single().kind)
            sync.commit(sameRest, emptySet())
            val excludedRest =
                sync.prepare("employee", monday, id, "rest-excluded", cells("X"), emptyList())
            sync.commit(excludedRest, emptySet())
            assertNotNull(provider.event(eventId))
            val excludedNewRest =
                sync.prepare("new-employee", monday, id, "rest-excluded", cells("X"), emptyList())
            assertTrue(excludedNewRest.changes.isEmpty())
            val workAgain =
                sync.prepare("employee", monday, id, "work", cells("18-21"), events("18-21"))
            assertEquals(ChangeKind.UPDATE, workAgain.changes.single().kind)
            sync.commit(workAgain, setOf(monday))
            assertFalse(provider.event(eventId)!!.allDay)
            assertEquals("Lavoro", provider.event(eventId)!!.title)
            val customOptions = EventOptions(reminderMinutes = 0, color = 0xff8e24aa.toInt())
            val preferences =
                sync.prepare(
                    "employee",
                    monday,
                    id,
                    "prefs",
                    cells("18-21"),
                    events("18-21", customOptions),
                )
            assertEquals(ChangeKind.UPDATE, preferences.changes.single().kind)
            sync.commit(preferences, setOf(monday))
            assertOptions(eventId, 0, customOptions.color)
            val removedOptions =
                sync.prepare(
                    "employee",
                    monday,
                    id,
                    "no-prefs",
                    cells("18-21"),
                    events("18-21", customOptions.copy(reminderMinutes = null, color = null)),
                )
            sync.commit(removedOptions, setOf(monday))
            assertOptions(eventId, null, null)
            val beforeEdit = requireNotNull(db.dao().shift(saved.id, "employee"))
            val hoursOptions = customOptions.copy(reminderMinutes = null, color = null)
            try {
                sync.updateHours(
                    "employee",
                    saved.id,
                    java.time.LocalTime.NOON,
                    java.time.LocalTime.NOON,
                    hoursOptions,
                    ZoneId.of("Europe/Rome"),
                )
                fail("Equal times must be rejected")
            } catch (_: IllegalArgumentException) {
                assertEquals(beforeEdit, db.dao().shift(saved.id, "employee"))
            }
            sync.updateHours(
                "employee",
                saved.id,
                java.time.LocalTime.of(20, 30),
                java.time.LocalTime.of(1, 0),
                hoursOptions,
                ZoneId.of("Europe/Rome"),
            )
            val afterEdit = requireNotNull(db.dao().shift(saved.id, "employee"))
            assertEquals(eventId, afterEdit.calendarEventId)
            assertEquals(beforeEdit.weekId, afterEdit.weekId)
            assertEquals(beforeEdit.createdAt, afterEdit.createdAt)
            assertTrue(afterEdit.updatedAt > beforeEdit.updatedAt)
            assertEquals("20:30", afterEdit.startTime)
            assertEquals("01:00", afterEdit.endTime)
            assertTrue(afterEdit.endNextDay)
            assertEquals(16200000L, provider.event(eventId)!!.let { it.end - it.start })
            assertOptions(eventId, null, null)
            val reimportEdited =
                sync.prepare(
                    "employee",
                    monday,
                    id,
                    "edited",
                    cells("20:30-01:00"),
                    events("20:30-01:00", hoursOptions),
                )
            assertEquals(ChangeKind.UNCHANGED, reimportEdited.changes.single().kind)
            fun calendarValues(values: ContentValues) {
                assertEquals(
                    1,
                    resolver.update(
                        calendarUri,
                        values,
                        "${CalendarContract.Calendars._ID} = ?",
                        arrayOf(id.toString()),
                    ),
                )
            }
            calendarValues(
                ContentValues().apply {
                    put(CalendarContract.Calendars.NAME, "renamed-work-calendar")
                    put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Lavoro")
                }
            )
            assertEquals("Lavoro", provider.calendars().single { it.id == id }.name)
            sync.updateHours(
                "employee",
                saved.id,
                java.time.LocalTime.of(21, 0),
                java.time.LocalTime.of(1, 0),
                hoursOptions,
                ZoneId.of("Europe/Rome"),
            )
            assertEquals(14400000L, provider.event(eventId)!!.let { it.end - it.start })
            calendarValues(
                ContentValues().apply {
                    put(CalendarContract.Calendars.VISIBLE, 0)
                }
            )
            assertFalse(provider.calendars().single { it.id == id }.visible)
            sync.updateHours(
                "employee",
                saved.id,
                java.time.LocalTime.of(22, 0),
                java.time.LocalTime.of(1, 0),
                hoursOptions,
                ZoneId.of("Europe/Rome"),
            )
            assertEquals(10800000L, provider.event(eventId)!!.let { it.end - it.start })
            val beforeReadOnly = requireNotNull(db.dao().shift(saved.id, "employee"))
            val eventBeforeReadOnly = provider.event(eventId)
            calendarValues(
                ContentValues().apply {
                    put(
                        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                        CalendarContract.Calendars.CAL_ACCESS_READ,
                    )
                }
            )
            try {
                sync.updateHours(
                    "employee",
                    saved.id,
                    java.time.LocalTime.of(23, 0),
                    java.time.LocalTime.of(1, 0),
                    hoursOptions,
                    ZoneId.of("Europe/Rome"),
                )
                fail("A read-only calendar must not update the event or local hours")
            } catch (error: IllegalArgumentException) {
                assertEquals("CALENDAR_NOT_WRITABLE", error.message)
            }
            assertEquals(beforeReadOnly, db.dao().shift(saved.id, "employee"))
            assertEquals(eventBeforeReadOnly, provider.event(eventId))
            calendarValues(
                ContentValues().apply {
                    put(
                        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                        CalendarContract.Calendars.CAL_ACCESS_OWNER,
                    )
                }
            )
            val movedDay = monday.plusDays(7)
            val convertedRest = requireNotNull(db.dao().shift(saved.id, "employee"))
            db.dao()
                .saveShift(
                    convertedRest.copy(
                        type = CellType.CODE.name,
                        code = "X",
                        startTime = null,
                        endTime = null,
                    )
                )
            val movedStart =
                movedDay.atTime(20, 0).atZone(ZoneId.of("Europe/Rome")).toInstant().toEpochMilli()
            resolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, movedStart)
                    put(CalendarContract.Events.DTEND, movedStart + 10800000L)
                },
                null,
                null,
            )
            sync.updateHours(
                "employee",
                saved.id,
                java.time.LocalTime.of(21, 0),
                java.time.LocalTime.of(23, 0),
                hoursOptions,
                ZoneId.of("Europe/Rome"),
            )
            assertEquals(CellType.WORK_SHIFT.name, db.dao().shift(saved.id, "employee")!!.type)
            assertNull(db.dao().shift(saved.id, "employee")!!.code)
            assertEquals(
                movedDay.atTime(21, 0).atZone(ZoneId.of("Europe/Rome")).toInstant().toEpochMilli(),
                provider.event(eventId)!!.start,
            )
            provider.enableForImport(id)
            val missing = sync.prepare("employee", monday, id, "hash3", cells(""), emptyList())
            assertEquals(ChangeKind.MISSING, missing.changes.single().kind)
            sync.commit(missing, emptySet())
            assertNotNull(provider.event(eventId))
            sync.commit(missing, setOf(monday))
            assertNull(provider.event(eventId))
            assertTrue(
                db.dao()
                    .previous("employee", monday.toString(), monday.plusDays(6).toString(), id)
                    .isEmpty()
            )
        } finally {
            db.close()
            resolver.delete(
                calendarUri,
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(id.toString()),
            )
            resolver.delete(
                calendarUri,
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(otherId.toString()),
            )
            resolver.delete(
                calendarUri,
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(readOnlyId.toString()),
            )
        }
    }
}
