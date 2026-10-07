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
                put(CalendarContract.Calendars.NAME, "turni-test")
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
        val db = Room.inMemoryDatabaseBuilder(context, TurniDatabase::class.java).build()
        try {
            val provider = CalendarRepository(resolver)
            val sync = SyncRepository(db, provider)
            val monday = LocalDate.of(2026, 10, 5)
            fun cells(raw: String) =
                (0L..6L).map { ShiftParser.parse(if (it == 0L) raw else "", monday.plusDays(it)) }
            fun events(raw: String) =
                listOf(
                    requireNotNull(
                        EventFactory.create(
                            cells(raw).first(),
                            emptyList(),
                            EventOptions("Turno test", reminderMinutes = 15),
                            ZoneId.of("Europe/Rome"),
                        )
                    )
                )
            assertTrue(provider.calendars().any { it.id == id })
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
        }
    }
}
