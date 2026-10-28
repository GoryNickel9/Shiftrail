package it.turni.app

import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.calendar.*
import it.turni.app.data.*
import it.turni.core.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ImportReceiptTest {
    @Test
    fun restIsExcludedAndReceiptsCountInsertionsRatherThanUpdatesOrRecovery() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val employee = "receipt-${UUID.randomUUID()}"
        val legacy = "$employee-legacy"
        val monday = LocalDate.of(2026, 11, 2)
        val cells =
            listOf("06-14", "X", "14-22", "X", "22-06", "X", "06-14").mapIndexed { index, raw ->
                ShiftParser.parse(raw, monday.plusDays(index.toLong()))
            }
        val options = EventOptions(saveRestDays = false)
        val zone = ZoneId.of("Europe/Rome")
        val sync = app.sync
        CalendarFixture("receipt-test").use { fixture ->
            try {
                val events = cells.mapNotNull {
                    EventFactory.create(it, emptyList(), options, zone)
                }
                val plan =
                    sync.prepare(
                        employee,
                        monday,
                        fixture.id,
                        "first",
                        cells,
                        events,
                        options,
                        zone,
                    )
                assertEquals(4, plan.changes.size)
                assertTrue(plan.changes.all { it.incoming?.allDay == false })
                // Simulate provider success followed by interrupted Room persistence.
                val event = events.first()
                app.calendar.apply(
                    fixture.id,
                    listOf(
                        CalendarMutation(
                            event.date,
                            event,
                            null,
                            eventMarker(employee, event.date, fixture.id),
                        )
                    ),
                )
                val first = sync.commitWithReceipt(plan, plan.changes.map { it.date }.toSet())
                assertEquals(4, first.changed)
                assertEquals(3, first.added)
                val repeated =
                    sync.prepare(
                        employee,
                        monday,
                        fixture.id,
                        "repeat",
                        cells,
                        events,
                        options,
                        zone,
                    )
                assertEquals(0, sync.commitWithReceipt(repeated, emptySet()).added)
                val alteredCells =
                    cells.toMutableList().apply { this[0] = ShiftParser.parse("08-16", monday) }
                val altered =
                    sync.prepare(
                        employee,
                        monday,
                        fixture.id,
                        "update",
                        alteredCells,
                        alteredCells.mapNotNull {
                            EventFactory.create(it, emptyList(), options, zone)
                        },
                        options,
                        zone,
                    )
                val updated = sync.commitWithReceipt(altered, setOf(monday))
                assertEquals(1, updated.changed)
                assertEquals(0, updated.added)
                val restCell = cells[1]
                val historicalOptions = options.copy(saveRestDays = true)
                val restEvent =
                    requireNotNull(
                        EventFactory.create(restCell, emptyList(), historicalOptions, zone)
                    )
                val old =
                    sync.prepare(
                        legacy,
                        monday,
                        fixture.id,
                        "legacy",
                        listOf(restCell),
                        listOf(restEvent),
                        historicalOptions,
                        zone,
                    )
                sync.commit(old, setOf(restCell.date))
                val oldId =
                    requireNotNull(
                        app.calendar.findMarker(
                            fixture.id,
                            eventMarker(legacy, restCell.date, fixture.id),
                        )
                    )
                val ignored =
                    sync.prepare(
                        legacy,
                        monday,
                        fixture.id,
                        "no-rest",
                        listOf(restCell),
                        emptyList(),
                        options,
                        zone,
                    )
                assertTrue(ignored.changes.isEmpty())
                assertEquals(0, sync.commitWithReceipt(ignored, emptySet()).added)
                assertNotNull(app.calendar.event(oldId))
            } finally {
                for (owner in listOf(employee, legacy)) app.database.openHelper.writableDatabase
                    .execSQL("DELETE FROM weeks WHERE employeeId = ?", arrayOf(owner))
            }
        }
    }
}
