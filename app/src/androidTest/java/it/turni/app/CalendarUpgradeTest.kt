package it.turni.app

import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.calendar.CalendarMutation
import it.turni.app.calendar.CalendarRepository
import it.turni.app.calendar.SyncRepository
import it.turni.app.data.TurniDatabase
import it.turni.core.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CalendarUpgradeTest {
    @get:Rule
    val migrations =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TurniDatabase::class.java)

    @Test
    fun versionOneKeepsOriginalCalendarAndImportsNewWeekWithoutMovingOldEvents() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "calendar-upgrade-test.db"
        CalendarFixture("upgrade-original").use { original ->
            CalendarFixture("upgrade-pc-sync").use { decoy ->
                val resolver = context.contentResolver
                resolver.update(
                    ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, original.id),
                    ContentValues().apply {
                        put(CalendarContract.Calendars.NAME, "original")
                        put(
                            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                            "Calendario originale",
                        )
                    },
                    null,
                    null,
                )
                val calendar = CalendarRepository(resolver)
                val monday = LocalDate.of(2026, 10, 26)
                val zone = ZoneId.of("Europe/Rome")
                val cell = ShiftParser.parse("09-17", monday)
                val base =
                    requireNotNull(
                        EventFactory.create(
                            cell,
                            emptyList(),
                            EventOptions(reminderMinutes = null, color = null),
                            zone,
                        )
                    )
                // Exact work-event signature and title used by EventFactory in commit 5820896
                // (0.2.0).
                val signature =
                    sha256(
                        "${cell.signature()}|${cell.start}|${cell.end}|null|${base.startMillis}|${base.endMillis}|Turno di lavoro|||null|null"
                    )
                val legacy = base.copy(title = "Turno di lavoro", contentSignature = signature)
                val oldId =
                    requireNotNull(
                        calendar
                            .apply(
                                original.id,
                                listOf(
                                    CalendarMutation(
                                        monday,
                                        legacy,
                                        null,
                                        eventMarker("employee", monday, original.id),
                                    )
                                ),
                            )[monday]
                    )
                val weekId = sha256("employee|$monday")
                val shiftId = sha256("employee|$monday|${original.id}")
                try {
                    migrations.createDatabase(name, 1).apply {
                        execSQL("INSERT INTO profiles VALUES ('employee', 'Test Employee', '')")
                        execSQL(
                            "INSERT INTO weeks VALUES (?, 'employee', ?, 1, 'legacy')",
                            arrayOf(weekId, monday.toString()),
                        )
                        execSQL(
                            "INSERT INTO settings VALUES (1, ?, 'Turno di lavoro', '', '', NULL, NULL, 0)",
                            arrayOf(original.id),
                        )
                        execSQL(
                            "INSERT INTO shifts VALUES (?, ?, 'employee', ?, '09:00', '17:00', 0, NULL, 'WORK_SHIFT', 1.0, ?, ?, ?, 1, 1)",
                            arrayOf<Any?>(
                                shiftId,
                                weekId,
                                monday.toString(),
                                oldId,
                                original.id,
                                signature,
                            ),
                        )
                        close()
                    }
                    migrations
                        .runMigrationsAndValidate(
                            name,
                            3,
                            true,
                            TurniDatabase.MIGRATION_1_2,
                            TurniDatabase.MIGRATION_2_3,
                        )
                        .close()
                    val db = Room.databaseBuilder(context, TurniDatabase::class.java, name).build()
                    try {
                        val settings = requireNotNull(db.dao().settings())
                        assertEquals(original.id, settings.calendarId)
                        assertEquals(oldId, db.dao().shifts(weekId).single().calendarEventId)
                        val sync = SyncRepository(db, calendar)
                        val options =
                            EventOptions(
                                reminderMinutes = settings.reminderMinutes,
                                color = settings.eventColor,
                            )
                        val nextMonday = monday.plusDays(7)
                        val cells =
                            (0L..6L).map { ShiftParser.parse("09-17", nextMonday.plusDays(it)) }
                        val events = cells.map {
                            requireNotNull(EventFactory.create(it, emptyList(), options, zone))
                        }
                        val plan =
                            sync.prepare(
                                "employee",
                                nextMonday,
                                requireNotNull(settings.calendarId),
                                "new-week",
                                cells,
                                events,
                            )
                        assertTrue(plan.changes.all { it.kind == ChangeKind.INSERT })
                        assertEquals(7, sync.commit(plan, cells.map { it.date }.toSet()))
                        val saved =
                            db.dao()
                                .previous(
                                    "employee",
                                    nextMonday.toString(),
                                    nextMonday.plusDays(6).toString(),
                                    original.id,
                                )
                        assertEquals(7, saved.size)
                        assertTrue(
                            saved.all {
                                calendar.event(it.calendarEventId)?.calendarId == original.id
                            }
                        )
                        assertNull(
                            calendar.findMarker(
                                decoy.id,
                                eventMarker("employee", nextMonday, decoy.id),
                            )
                        )
                        assertEquals("Turno di lavoro", calendar.event(oldId)!!.title)
                        val reimport =
                            sync.prepare(
                                "employee",
                                monday,
                                original.id,
                                "legacy",
                                listOf(cell),
                                listOf(
                                    requireNotNull(
                                        EventFactory.create(cell, emptyList(), options, zone)
                                    )
                                ),
                            )
                        assertEquals(ChangeKind.UPDATE, reimport.changes.single().kind)
                        assertEquals(0, sync.commit(reimport, emptySet()))
                        assertEquals("Turno di lavoro", calendar.event(oldId)!!.title)
                        assertEquals(1, sync.commit(reimport, setOf(monday)))
                        assertEquals(oldId, db.dao().shifts(weekId).single().calendarEventId)
                        assertEquals("Lavoro", calendar.event(oldId)!!.title)
                    } finally {
                        db.close()
                    }
                } finally {
                    context.deleteDatabase(name)
                }
            }
        }
    }
}
