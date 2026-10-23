package it.turni.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.data.*
import it.turni.core.*
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ShiftHoursUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun editImportedHoursUpdatesCalendarAndTotalsWithoutDuplicatingTheShift() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).forEach {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it)
        }
        val app = context.applicationContext as TurniApplication
        val dao = app.database.dao()
        val oldProfile = runBlocking { dao.profile() }
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Impostazioni").fetchSemanticsNodes().isNotEmpty()
        }
        if (oldProfile == null) {
            compose.onNodeWithText("Impostazioni").performClick()
            compose.onNodeWithText("Nome e cognome").performTextInput("Marta Neri")
            compose.onNodeWithText("Salva").performClick()
            compose.waitUntil(15000) {
                compose
                    .onAllNodesWithText("Importa per Marta Neri")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
        val profile = requireNotNull(runBlocking { dao.profile() })
        val date = YearMonth.now().atDay(5)
        val weekId = "edit-hours-week"
        val shiftId = "edit-hours-shift"
        val resolver = context.contentResolver
        val calendarUri =
            CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "hours-edit-test")
                .appendQueryParameter(
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.ACCOUNT_TYPE_LOCAL,
                )
                .build()
        val calendarId =
            ContentUris.parseId(
                requireNotNull(
                    resolver.insert(
                        calendarUri,
                        ContentValues().apply {
                            put(CalendarContract.Calendars.ACCOUNT_NAME, "hours-edit-test")
                            put(
                                CalendarContract.Calendars.ACCOUNT_TYPE,
                                CalendarContract.ACCOUNT_TYPE_LOCAL,
                            )
                            put(CalendarContract.Calendars.NAME, "PC Sync")
                            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "PC Sync")
                            put(CalendarContract.Calendars.OWNER_ACCOUNT, "hours-edit-test")
                            put(
                                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                                CalendarContract.Calendars.CAL_ACCESS_OWNER,
                            )
                            put(CalendarContract.Calendars.VISIBLE, 1)
                            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "Europe/Rome")
                            put(CalendarContract.Calendars.ALLOWED_REMINDERS, "1")
                        },
                    )
                )
            )
        try {
            val eventId = runBlocking {
                val cell = ShiftParser.parse("19:00-01:00", date)
                val event =
                    requireNotNull(
                        EventFactory.create(
                            cell,
                            emptyList(),
                            EventOptions(),
                            ZoneId.systemDefault(),
                        )
                    )
                val id =
                    requireNotNull(
                        app.calendar
                            .apply(
                                calendarId,
                                listOf(
                                    it.turni.app.calendar.CalendarMutation(
                                        date,
                                        event,
                                        null,
                                        eventMarker(profile.id, date, calendarId),
                                    )
                                ),
                            )[date]
                    )
                dao.saveWeek(
                    WeekEntity(
                        weekId,
                        profile.id,
                        date.with(java.time.DayOfWeek.MONDAY).toString(),
                        1,
                        "original-hash",
                    )
                )
                dao.saveShift(
                    ShiftEntity(
                        shiftId,
                        weekId,
                        profile.id,
                        date.toString(),
                        "19:00",
                        "01:00",
                        true,
                        null,
                        CellType.WORK_SHIFT.name,
                        0.95,
                        id,
                        calendarId,
                        event.contentSignature,
                        1,
                        2,
                    )
                )
                id
            }
            // The linked calendar can be renamed or hidden after the original import.
            resolver.update(
                calendarUri,
                ContentValues().apply {
                    put(CalendarContract.Calendars.NAME, "employee-calendar")
                    put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Calendario lavoro")
                    put(CalendarContract.Calendars.VISIBLE, 0)
                },
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(calendarId.toString()),
            )
            assertFalse(runBlocking { app.calendar.calendars().any { it.id == calendarId } })
            compose.waitUntil(15000) {
                compose
                    .onAllNodesWithText("Importa per ${profile.name}")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText("Ore").performClick()
            waitForTotal("6 h 00 min")
            openEdit(shiftId)
            compose.onNodeWithText("Modifica orario turno").assertIsDisplayed()
            compose.onNodeWithTag("extra_date").assertTextContains(date.toString())
            compose.onNodeWithTag("extra_start").assertTextContains("19:00")
            compose.onNodeWithTag("extra_start").performTextReplacement("25:00")
            compose.onNodeWithText("Salva").performClick()
            compose
                .onNodeWithText("Inserisci una data valida e due orari diversi nel formato HH:MM.")
                .performScrollTo()
                .assertIsDisplayed()
            assertEquals("19:00", runBlocking { dao.shift(shiftId, profile.id) }!!.startTime)
            compose.onNodeWithTag("extra_start").performScrollTo().performTextReplacement("01:00")
            compose.onNodeWithText("Salva").performClick()
            compose
                .onNodeWithText("Inserisci una data valida e due orari diversi nel formato HH:MM.")
                .performScrollTo()
                .assertIsDisplayed()
            compose.onNodeWithTag("extra_start").performScrollTo().performTextReplacement("20:30")
            compose.onNodeWithTag("extra_start").assertTextContains("20:30")
            compose.waitForIdle()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("extra_start").assertTextContains("20:30")
            compose.onNodeWithText("Salva").performClick()
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("Modifica orario turno").fetchSemanticsNodes().isEmpty()
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            waitForTotal("4 h 30 min")
            val saved = requireNotNull(runBlocking { dao.shift(shiftId, profile.id) })
            assertEquals(eventId, saved.calendarEventId)
            assertEquals(weekId, saved.weekId)
            assertEquals("20:30", saved.startTime)
            assertEquals("01:00", saved.endTime)
            assertTrue(saved.endNextDay)
            val event = requireNotNull(runBlocking { app.calendar.event(eventId) })
            assertEquals(16200000L, event.end - event.start)
            assertEquals(
                eventId,
                runBlocking {
                    app.calendar.findMarker(calendarId, eventMarker(profile.id, date, calendarId))
                },
            )
            assertEquals(
                "original-hash",
                requireNotNull(runBlocking { dao.byHash(profile.id, "original-hash") }).sourceHash,
            )
            compose.activityRule.scenario.recreate()
            waitForTotal("4 h 30 min")
            openEdit(shiftId)
            compose.onNodeWithTag("extra_start").assertTextContains("20:30")
            compose.onNodeWithTag("extra_start").performTextReplacement("21:00")
            compose.onNodeWithText("Annulla").performClick()
            assertEquals("20:30", runBlocking { dao.shift(shiftId, profile.id) }!!.startTime)
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            fun changeHours(hour: Int) {
                resolver.update(
                    eventUri,
                    ContentValues().apply {
                        put(
                            CalendarContract.Events.DTSTART,
                            date
                                .atTime(hour, 0)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli(),
                        )
                    },
                    null,
                    null,
                )
            }
            changeHours(22)
            waitForTotal("3 h 00 min")
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            changeHours(21)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            waitForTotal("4 h 00 min")
            compose.activityRule.scenario.recreate()
            waitForTotal("4 h 00 min")
            resolver.delete(eventUri, null, null)
            waitForTotal("0 h 00 min")
        } finally {
            runBlocking {
                dao.removeShift(shiftId)
                app.database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM weeks WHERE id = ?",
                    arrayOf(weekId),
                )
                if (oldProfile == null)
                    app.database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM profiles WHERE id = ?",
                        arrayOf(profile.id),
                    )
            }
            resolver.delete(
                calendarUri,
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(calendarId.toString()),
            )
        }
    }

    private fun openEdit(id: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("edit_$id"))
        compose.onNodeWithTag("edit_$id").performClick()
    }

    private fun waitForTotal(text: String) {
        compose.waitUntil(15000) {
            compose
                .onAllNodes(hasTestTag("hours_total") and hasText(text))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
