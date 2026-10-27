package it.turni.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    @get:Rule
    val migrations =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            TurniDatabase::class.java,
        )

    @Test
    fun migrationPreservesProfileShiftsAndAddsCalendarPreferencesAndExtraWork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-test.db"
        try {
            migrations.createDatabase(name, 1).apply {
                execSQL("INSERT INTO profiles VALUES ('employee', 'Luca Baldeddu', '')")
                execSQL("INSERT INTO weeks VALUES ('week', 'employee', '2026-10-05', 1, 'hash')")
                execSQL(
                    "INSERT INTO shifts VALUES ('shift', 'week', 'employee', '2026-10-05', '19:00', '01:00', 1, NULL, 'WORK_SHIFT', 0.95, 123, 1, 'sig', 1, 1)"
                )
                execSQL("INSERT INTO settings VALUES (1, 1, 'Old title', '', '', NULL, NULL, 0)")
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
                assertEquals("Luca Baldeddu", db.dao().profile()!!.name)
                assertEquals(123L, db.dao().shifts("week").single().calendarEventId)
                assertTrue(db.dao().shifts("week").single().endNextDay)
                val legacy = db.dao().shifts("week").single()
                val legacyDate = java.time.LocalDate.parse(legacy.date)
                val legacyPeriod =
                    it.turni.core.WorkPeriod(
                        legacy.id,
                        legacyDate,
                        java.time.LocalTime.parse(legacy.startTime),
                        java.time.LocalTime.parse(legacy.endTime),
                        it.turni.core.WorkSource.SCHEDULE,
                    )
                assertEquals(
                    60L,
                    it.turni.core.WorkingHours.summarize(
                            listOf(legacyPeriod),
                            it.turni.core.HoursWindow(legacyDate, legacyDate.plusDays(1)),
                        )
                        .nightMinutes,
                )
                val settings = db.dao().settings()!!
                assertEquals("Lavoro", settings.title)
                assertEquals(30, settings.reminderMinutes)
                assertEquals(0xff1a73e8.toInt(), settings.eventColor)
                assertEquals(0xff0b8043.toInt(), settings.restColor)
                assertTrue(settings.saveRestDays)
                assertFalse(settings.notificationsEnabled)
                assertEquals(30, settings.notificationMinutes)
                assertTrue(settings.notificationSound)
                assertTrue(settings.notificationVibration)
                db.dao()
                    .saveSettings(
                        settings.copy(
                            notificationsEnabled = true,
                            notificationMinutes = 95,
                            notificationSound = false,
                            notificationVibration = false,
                        )
                    )
                assertEquals(95, db.dao().settings()!!.notificationMinutes)
                assertFalse(db.dao().settings()!!.notificationSound)
                val extra = ExtraWorkEntity("extra", "employee", "2026-10-06", "23:00", "01:00", 2)
                db.dao().saveExtra(extra)
                db.dao().saveExtra(extra.copy(endTime = "02:00"))
                assertEquals(
                    "02:00",
                    db.dao().findExtra("employee", extra.date, "23:00", "02:00")!!.endTime,
                )
                db.dao().removeExtra(extra.id, "someone-else")
                assertNotNull(db.dao().findExtra("employee", extra.date, "23:00", "02:00"))
                db.dao().removeExtra(extra.id, "employee")
                assertNull(db.dao().findExtra("employee", extra.date, "23:00", "02:00"))
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationFromTwoPreservesSettingsAndExtraWork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-notifications-test.db"
        try {
            migrations.createDatabase(name, 2).apply {
                execSQL(
                    "INSERT INTO settings VALUES (1, 42, 'Lavoro', 'Place', 'Details', 15, 1234, 0, 5678, 0)"
                )
                execSQL(
                    "INSERT INTO extra_work VALUES ('extra', 'employee', '2026-10-27', '00:00', '06:00', 1)"
                )
                close()
            }
            migrations.runMigrationsAndValidate(name, 3, true, TurniDatabase.MIGRATION_2_3).close()
            val db = Room.databaseBuilder(context, TurniDatabase::class.java, name).build()
            try {
                val settings = requireNotNull(db.dao().settings())
                assertEquals(42L, settings.calendarId)
                assertEquals(15, settings.reminderMinutes)
                assertEquals(1234, settings.eventColor)
                assertEquals(5678, settings.restColor)
                assertFalse(settings.saveRestDays)
                assertFalse(settings.notificationsEnabled)
                assertEquals(30, settings.notificationMinutes)
                assertNotNull(db.dao().findExtra("employee", "2026-10-27", "00:00", "06:00"))
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationRetainsPreviouslyConfiguredReminderAndWorkColor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-custom-test.db"
        try {
            migrations.createDatabase(name, 1).apply {
                execSQL(
                    "INSERT INTO settings VALUES (1, 12, 'Old title', 'Place', 'Description', 15, 1234, 0)"
                )
                close()
            }
            migrations.runMigrationsAndValidate(name, 2, true, TurniDatabase.MIGRATION_1_2).apply {
                query(
                        "SELECT title, reminderMinutes, eventColor, calendarId, location, description FROM settings"
                    )
                    .use {
                        assertTrue(it.moveToFirst())
                        assertEquals("Lavoro", it.getString(0))
                        assertEquals(15, it.getInt(1))
                        assertEquals(1234, it.getInt(2))
                        assertEquals(12L, it.getLong(3))
                        assertEquals("Place", it.getString(4))
                        assertEquals("Description", it.getString(5))
                    }
                close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun roomRoundTripAndUpsertDoesNotDuplicate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TurniDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.saveProfile(ProfileEntity("employee", "Luca Baldeddu", "BALDEDDU"))
            assertEquals("BALDEDDU", dao.profile()?.aliases)
            dao.saveWeek(WeekEntity("week", "employee", "2026-10-05", 1, "hash"))
            val shift =
                ShiftEntity(
                    "shift",
                    "week",
                    "employee",
                    "2026-10-05",
                    "19:00",
                    "01:00",
                    true,
                    null,
                    "WORK_SHIFT",
                    0.95,
                    123,
                    1,
                    "sig",
                    1,
                    1,
                )
            dao.saveShift(shift)
            dao.saveShift(shift.copy(startTime = "18:30", updatedAt = 2))
            assertEquals(1, dao.shifts("week").size)
            assertEquals("18:30", dao.shifts("week").single().startTime)
            assertEquals("week", dao.byHash("employee", "hash")?.id)
        } finally {
            db.close()
        }
    }
}
