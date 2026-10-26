package it.turni.app

import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.calendar.*
import it.turni.app.data.*
import it.turni.core.*
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtraHoursIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Europe/Rome")
    private val options = EventOptions(reminderMinutes = 15)

    private suspend fun withCalendar(
        block: suspend (TurniDatabase, CalendarRepository, SyncRepository, Long) -> Unit
    ) {
        CalendarFixture("extra-hours-test").use { fixture ->
            val db = Room.inMemoryDatabaseBuilder(context, TurniDatabase::class.java).build()
            try {
                val provider = CalendarRepository(context.contentResolver)
                block(db, provider, SyncRepository(db, provider), fixture.id)
            } finally {
                db.close()
            }
        }
    }

    private suspend fun importShift(
        sync: SyncRepository,
        date: LocalDate,
        calendar: Long,
        raw: String = "16-18",
    ) {
        val cell = ShiftParser.parse(raw, date)
        val event = requireNotNull(EventFactory.create(cell, emptyList(), options, zone))
        val plan =
            sync.prepare(
                "employee",
                date.with(DayOfWeek.MONDAY),
                calendar,
                "photo",
                listOf(cell),
                listOf(event),
            )
        sync.commit(plan, setOf(date))
    }

    private fun extra(id: String, date: LocalDate, start: String, end: String) =
        ExtraWorkEntity(id, "employee", date.toString(), start, end, 0)

    private fun assertTimes(event: ProviderEvent?, date: LocalDate, start: String, end: String) {
        val actual = requireNotNull(event)
        assertEquals(
            date.atTime(LocalTime.parse(start)).atZone(zone).toInstant().toEpochMilli(),
            actual.start,
        )
        val endDate = date.plusDays(if (LocalTime.parse(end) < LocalTime.parse(start)) 1 else 0)
        assertEquals(
            endDate.atTime(LocalTime.parse(end)).atZone(zone).toInstant().toEpochMilli(),
            actual.end,
        )
    }

    @Test
    fun extrasUpdatePastAndFutureEventsAndHomeWithoutDoubleCounting() = runBlocking {
        withCalendar { db, provider, sync, calendar ->
            for (date in listOf(LocalDate.of(2020, 10, 8), LocalDate.now().plusDays(1))) {
                importShift(sync, date, calendar)
                val original =
                    db.dao().allRecordedShifts("employee").first().single {
                        it.date == date.toString()
                    }
                val early = extra("early-$date", date, "13:00", "16:00")
                val late = extra("late-$date", date, "18:00", "20:00")
                sync.saveExtra(early, null, calendar, options, zone)
                assertTimes(provider.event(original.calendarEventId), date, "13:00", "18:00")
                sync.saveExtra(late, null, calendar, options, zone)
                assertTimes(provider.event(original.calendarEventId), date, "13:00", "20:00")
                val saved = requireNotNull(db.dao().shift(original.id, "employee"))
                assertEquals(original.calendarEventId, saved.calendarEventId)
                assertEquals("16:00", saved.startTime)
                val extras = db.dao().allExtraWork("employee").first()
                val schedule =
                    calendarSchedule(
                        listOf(saved),
                        provider.events(listOf(saved.calendarEventId)),
                        zone,
                        extras,
                    )
                assertEquals("13:00", schedule.shifts.single().startTime)
                assertEquals("20:00", schedule.shifts.single().endTime)
                val hours =
                    WorkingHours.summarize(
                        schedule.periods + extras.map { it.period() },
                        HoursWindow(date, date),
                    )
                assertEquals(120L, hours.workMinutes)
                assertEquals(300L, hours.extraMinutes)
                assertEquals(420L, hours.totalMinutes)
                importShift(sync, date, calendar)
                assertTimes(provider.event(saved.calendarEventId), date, "13:00", "20:00")
                val restarted = SyncRepository(db, provider)
                restarted.removeExtra(early, options, zone)
                assertTimes(provider.event(saved.calendarEventId), date, "16:00", "20:00")
                restarted.removeExtra(late, options, zone)
                assertTimes(provider.event(saved.calendarEventId), date, "16:00", "18:00")
            }
        }
    }

    @Test
    fun editMoveAndDeleteExtraRecalculateBothDaysAndPreserveImportedWeek() = runBlocking {
        withCalendar { db, provider, sync, calendar ->
            val date = LocalDate.of(2020, 10, 8)
            importShift(sync, date, calendar)
            val original = db.dao().allRecordedShifts("employee").first().single()
            val week = db.dao().weekFor("employee", date.with(DayOfWeek.MONDAY).toString())
            val early = extra("early", date, "13:00", "16:00")
            sync.saveExtra(early, null, calendar, options, zone)
            val edited = early.copy(startTime = "12:00")
            sync.saveExtra(edited, early, calendar, options, zone)
            assertTimes(provider.event(original.calendarEventId), date, "12:00", "18:00")
            val moved =
                edited.copy(
                    date = date.plusDays(1).toString(),
                    startTime = "22:00",
                    endTime = "02:00",
                )
            sync.saveExtra(moved, edited, calendar, options, zone)
            assertTimes(provider.event(original.calendarEventId), date, "16:00", "18:00")
            val standalone =
                db.dao().allRecordedShifts("employee").first().single { it.date == moved.date }
            assertTimes(
                provider.event(standalone.calendarEventId),
                date.plusDays(1),
                "22:00",
                "02:00",
            )
            assertEquals(week, db.dao().weekFor("employee", date.with(DayOfWeek.MONDAY).toString()))
            sync.removeExtra(moved, options, zone)
            assertNull(provider.event(standalone.calendarEventId))
            assertEquals(1, db.dao().allRecordedShifts("employee").first().size)
            assertTrue(db.dao().allExtraWork("employee").first().isEmpty())
        }
    }

    @Test
    fun extraOnRestRestoresAllDayEventAndBaseEditKeepsExtras() = runBlocking {
        withCalendar { db, provider, sync, calendar ->
            val date = LocalDate.of(2020, 10, 8)
            importShift(sync, date, calendar, "X")
            val original = db.dao().allRecordedShifts("employee").first().single()
            val early = extra("early", date, "13:00", "16:00")
            sync.saveExtra(early, null, calendar, options, zone)
            assertTimes(provider.event(original.calendarEventId), date, "13:00", "16:00")
            sync.removeExtra(early, options, zone)
            assertTrue(provider.event(original.calendarEventId)!!.allDay)
            importShift(sync, date, calendar)
            sync.saveExtra(early, null, calendar, options, zone)
            sync.updateHours(
                "employee",
                original.id,
                LocalTime.of(16, 0),
                LocalTime.of(19, 0),
                options,
                zone,
            )
            assertTimes(provider.event(original.calendarEventId), date, "13:00", "19:00")
            sync.removeExtra(early, options, zone)
            assertTimes(provider.event(original.calendarEventId), date, "16:00", "19:00")
        }
    }

    @Test
    fun interruptedWriteRecoversAndExternalCalendarEditsAreNotOverwritten() = runBlocking {
        withCalendar { db, provider, sync, calendar ->
            val date = LocalDate.of(2020, 10, 8)
            importShift(sync, date, calendar)
            val original = db.dao().allRecordedShifts("employee").first().single()
            val early = extra("early", date, "13:00", "16:00")
            val uri =
                ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI,
                    original.calendarEventId,
                )
            // Simulate a provider commit followed by an interrupted Room commit.
            context.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(
                        CalendarContract.Events.DTSTART,
                        date.atTime(13, 0).atZone(zone).toInstant().toEpochMilli(),
                    )
                },
                null,
                null,
            )
            sync.saveExtra(early, null, calendar, options, zone)
            assertEquals("16:00", db.dao().shift(original.id, "employee")!!.startTime)
            sync.removeExtra(early, options, zone)
            assertTimes(provider.event(original.calendarEventId), date, "16:00", "18:00")
            sync.saveExtra(early, null, calendar, options, zone)
            context.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(
                        CalendarContract.Events.DTEND,
                        date.atTime(21, 0).atZone(zone).toInstant().toEpochMilli(),
                    )
                },
                null,
                null,
            )
            try {
                sync.saveExtra(early.copy(startTime = "12:00"), early, calendar, options, zone)
                fail("An external edit must not be silently discarded")
            } catch (error: IllegalArgumentException) {
                assertEquals("CALENDAR_EXTRA_CONFLICT", error.message)
            }
            assertTimes(provider.event(original.calendarEventId), date, "13:00", "21:00")
            assertEquals(listOf(early), db.dao().allExtraWork("employee").first())
        }
    }

    @Test
    fun duplicateAndReadOnlyCalendarDoNotSaveLocalExtras() = runBlocking {
        withCalendar { db, provider, sync, calendar ->
            val date = LocalDate.of(2020, 10, 8)
            importShift(sync, date, calendar)
            val early = extra("early", date, "13:00", "16:00")
            sync.saveExtra(early, null, calendar, options, zone)
            try {
                sync.saveExtra(early.copy(id = "duplicate"), null, calendar, options, zone)
                fail("Duplicate must be rejected")
            } catch (error: IllegalArgumentException) {
                assertEquals("EXTRA_DUPLICATE", error.message)
            }
            val eventId = db.dao().allRecordedShifts("employee").first().single().calendarEventId
            val before = provider.event(eventId)
            context.contentResolver.update(
                ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendar)
                    .buildUpon()
                    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                    .appendQueryParameter(
                        CalendarContract.Calendars.ACCOUNT_NAME,
                        "extra-hours-test",
                    )
                    .appendQueryParameter(
                        CalendarContract.Calendars.ACCOUNT_TYPE,
                        CalendarContract.ACCOUNT_TYPE_LOCAL,
                    )
                    .build(),
                ContentValues().apply {
                    put(
                        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                        CalendarContract.Calendars.CAL_ACCESS_READ,
                    )
                },
                null,
                null,
            )
            try {
                sync.saveExtra(early.copy(startTime = "12:00"), early, calendar, options, zone)
                fail("Read-only calendar must reject extra edits")
            } catch (error: IllegalArgumentException) {
                assertEquals("CALENDAR_NOT_WRITABLE", error.message)
            }
            assertEquals(listOf(early), db.dao().allExtraWork("employee").first())
            assertEquals(before, provider.event(eventId))
        }
    }
}
