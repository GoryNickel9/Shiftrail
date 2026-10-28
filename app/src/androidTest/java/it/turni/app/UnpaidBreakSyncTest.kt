package it.turni.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.calendar.*
import it.turni.app.data.*
import it.turni.core.*
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UnpaidBreakSyncTest {
    @Test
    fun importedShiftEditsProtectBreaksAndDeletionCleansThem() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TurniDatabase::class.java).build()
        try {
            CalendarFixture("break-sync-test").use { fixture ->
                val calendar = CalendarRepository(context.contentResolver)
                val sync = SyncRepository(db, calendar)
                val date = LocalDate.of(2026, 10, 26)
                val zone = ZoneId.systemDefault()
                val cell = ShiftParser.parse("22-06", date)
                val event =
                    requireNotNull(EventFactory.create(cell, emptyList(), EventOptions(), zone))
                val plan =
                    sync.prepare(
                        "employee",
                        date,
                        fixture.id,
                        "break-sync",
                        listOf(cell),
                        listOf(event),
                    )
                sync.commit(plan, setOf(date))
                val dao = db.dao()
                val shift = dao.allRecordedShifts("employee").first().single()
                val pause =
                    UnpaidBreakEntity(
                        "pause",
                        "employee",
                        shift.id,
                        date.plusDays(1).atTime(2, 0).toString(),
                        date.plusDays(1).atTime(2, 30).toString(),
                    )
                dao.saveBreak(pause)
                val original = requireNotNull(calendar.event(shift.calendarEventId))
                val rejected = runCatching {
                    sync.updateHours(
                        "employee",
                        shift.id,
                        LocalTime.of(22, 0),
                        LocalTime.of(2, 0),
                        EventOptions(),
                        zone,
                    )
                }
                assertEquals("BREAK_OUTSIDE_SHIFT", rejected.exceptionOrNull()?.message)
                assertEquals(original.end, calendar.event(original.id)!!.end)
                sync.updateHours(
                    "employee",
                    shift.id,
                    LocalTime.of(22, 0),
                    LocalTime.of(7, 0),
                    EventOptions(),
                    zone,
                )
                val saved = requireNotNull(dao.shift(shift.id, "employee"))
                val periods =
                    calendarWorkPeriods(
                            listOf(saved),
                            calendar.events(listOf(saved.calendarEventId)),
                            zone,
                        )
                        .map {
                            it.copy(breaks = dao.breaks("employee", it.id).map { b -> b.domain() })
                        }
                val result = WorkingHours.summarize(periods, HoursWindow(date, date.plusDays(1)))
                assertEquals(510L, result.totalMinutes)
                assertEquals(330L, result.nightMinutes)
                assertEquals(listOf(pause), dao.breaks("employee", shift.id))
                dao.removeShift(shift.id)
                assertTrue(dao.breaks("employee", shift.id).isEmpty())
            }
        } finally {
            db.close()
        }
    }
}
