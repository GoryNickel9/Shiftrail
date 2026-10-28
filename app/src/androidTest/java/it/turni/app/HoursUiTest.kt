package it.turni.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.calendar.CalendarMutation
import it.turni.app.data.*
import it.turni.app.ui.AppViewModel
import it.turni.core.*
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HoursUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun savedShiftsMonthWeekAndManualExtraSurviveRestartWithoutDuplicatingImportedHours() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as TurniApplication
        val dao = app.database.dao()
        val oldSettings = runBlocking { dao.settings() } ?: SettingsEntity()
        val month = YearMonth.now()
        val oldProfile = runBlocking { dao.profile() }
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Impostazioni").fetchSemanticsNodes().isNotEmpty()
        }
        lateinit var readyViewModel: AppViewModel
        compose.runOnIdle {
            readyViewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        }
        compose.waitUntil(15000) {
            readyViewModel.state.value.loaded && !readyViewModel.state.value.busy
        }
        if (oldProfile == null) {
            compose.onNodeWithText("Impostazioni").performClick()
            compose.onNodeWithText("Nome e cognome").performTextInput("Marta Neri")
            compose.onNodeWithText("Salva").performClick()
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("home_import_actions").fetchSemanticsNodes().isNotEmpty()
            }
        }
        val profile = requireNotNull(runBlocking { dao.profile() })
        val employee = profile.id
        val ids = listOf("hours-incoming", "hours-night", "hours-old-calendar", "hours-rest")
        val primary = CalendarFixture("hours-primary")
        val secondary = CalendarFixture("hours-secondary")
        try {
            runBlocking {
                dao.saveWeek(
                    WeekEntity(
                        "hours-test-week",
                        employee,
                        month.atDay(1).toString(),
                        1,
                        "hours-test",
                    )
                )
                suspend fun shift(
                    id: String,
                    date: String,
                    start: String?,
                    end: String?,
                    calendar: Long,
                    updated: Long,
                    code: String? = null,
                ): ShiftEntity {
                    val cell =
                        ShiftParser.parse(code ?: "$start-$end", java.time.LocalDate.parse(date))
                    val event =
                        requireNotNull(
                            EventFactory.create(
                                cell,
                                emptyList(),
                                EventOptions(),
                                java.time.ZoneId.systemDefault(),
                            )
                        )
                    val eventId =
                        requireNotNull(
                            app.calendar
                                .apply(
                                    calendar,
                                    listOf(
                                        CalendarMutation(
                                            cell.date,
                                            event,
                                            null,
                                            eventMarker(employee, cell.date, calendar),
                                        )
                                    ),
                                )[cell.date]
                        )
                    return ShiftEntity(
                        id,
                        "hours-test-week",
                        employee,
                        date,
                        start,
                        end,
                        end != null && start != null && end < start,
                        code,
                        if (code == null) "WORK_SHIFT" else "CODE",
                        0.95,
                        eventId,
                        calendar,
                        id,
                        1,
                        updated,
                    )
                }
                dao.saveShift(
                    shift(
                        ids[0],
                        month.atDay(1).minusDays(1).toString(),
                        "23:00",
                        "02:00",
                        primary.id,
                        2,
                    )
                )
                dao.saveShift(
                    shift(ids[1], month.atDay(5).toString(), "19:00", "01:00", primary.id, 2)
                )
                dao.saveShift(
                    shift(ids[2], month.atDay(5).toString(), "18:00", "01:00", secondary.id, 1)
                )
                dao.saveShift(
                    shift(ids[3], month.atDay(6).toString(), null, null, primary.id, 2, "X")
                )
            }
            var calendarSelected = false
            compose.runOnIdle {
                ViewModelProvider(compose.activity)[AppViewModel::class.java].saveCalendar(
                    primary.id,
                    false,
                ) {
                    calendarSelected = true
                }
            }
            compose.waitUntil(15000) { calendarSelected }
            waitForHome()
            compose.onNodeWithText("Orario").performClick()
            waitForTotal("8 h 00 min")
            compose.onNodeWithTag("hours_work").assertTextEquals("8 h 00 min")
            compose.onNodeWithTag("hours_extra").assertTextEquals("0 h 00 min")
            compose.onNodeWithTag("hours_night").assertTextEquals("3 h 00 min")
            compose.onNodeWithText("Aggiungi straordinari").performClick()
            compose.onNodeWithTag("extra_date").performTextClearance()
            compose.onNodeWithTag("extra_date").performTextInput(month.atDay(8).toString())
            compose.selectTime("extra_start", "22:00")
            compose.selectTime("extra_end", "01:00")
            compose.onNodeWithText("Salva").performClick()
            waitForTotal("11 h 00 min")
            compose.onNodeWithTag("hours_night").assertTextEquals("4 h 00 min")
            val extraId =
                requireNotNull(
                        runBlocking {
                            dao.findExtra(employee, month.atDay(8).toString(), "22:00", "01:00")
                        }
                    )
                    .id
            compose
                .onNode(hasScrollToIndexAction())
                .performScrollToNode(hasTestTag("edit_$extraId"))
            compose.onNodeWithTag("edit_$extraId").performClick()
            compose.selectTime("extra_start", "22:30")
            compose.onNodeWithTag("extra_start").assertTextContains("22:30")
            compose.waitForIdle()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("extra_start").assertTextContains("22:30")
            compose.onNodeWithText("Salva").performClick()
            compose.waitUntil(15000) {
                readyViewModel.state.value.error?.let { throw AssertionError("Save overtime: $it") }
                compose.onAllNodesWithText("Modifica straordinari").fetchSemanticsNodes().isEmpty()
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            waitForTotal("10 h 30 min")
            saveScreenshot("hours-month.png")
            compose.onNodeWithTag("choose_week").performClick()
            val week = WorkingHours.monthWeeks(month).first()
            val format = DateTimeFormatter.ofPattern("dd/MM")
            compose.onNodeWithTag("week_option_0").performScrollTo().performClick()
            compose.onNodeWithText("Mostra settimana").performClick()
            waitForTotal(if (week.until.dayOfMonth >= 5) "8 h 00 min" else "2 h 00 min")
            saveScreenshot("hours-week.png")
            compose.onNodeWithTag("choose_week").performClick()
            compose.onNodeWithText("Tutto il mese").performClick()
            compose.onNodeWithText("Mostra mese").performClick()
            waitForTotal("10 h 30 min")
            compose.onNodeWithTag("previous_month").performClick()
            waitForTotal("1 h 00 min")
            compose.onNodeWithTag("next_month").performClick()
            waitForTotal("10 h 30 min")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("hours_total").fetchSemanticsNodes().isNotEmpty()
            }
            waitForTotal("10 h 30 min")
            compose
                .onNode(hasScrollToIndexAction())
                .performScrollToNode(hasContentDescription("Elimina"))
            compose.onNodeWithContentDescription("Elimina").performClick()
            compose.onNodeWithText("Eliminare gli straordinari?").assertIsDisplayed()
            compose.onNodeWithTag("extra_delete_confirm").performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            waitForTotal("8 h 00 min")
            assertNull(
                runBlocking { dao.findExtra(employee, month.atDay(8).toString(), "22:30", "01:00") }
            )
            compose.onNodeWithTag("choose_week").performClick()
            compose.onNodeWithTag("week_option_0").performScrollTo().performClick()
            compose.onNodeWithText("Mostra settimana").performClick()
            compose.onNodeWithText("Aggiungi straordinari").performClick()
            compose.onNodeWithTag("extra_date").performTextClearance()
            compose
                .onNodeWithTag("extra_date")
                .performTextInput(month.minusMonths(1).atDay(3).toString())
            compose.selectTime("extra_start", "09:00")
            compose.selectTime("extra_end", "10:15")
            compose.onNodeWithText("Salva").performClick()
            waitForTotal("2 h 15 min")
            compose.onNodeWithTag("choose_week").assertTextContains("Settimana")
            compose
                .onNode(hasScrollToIndexAction())
                .performScrollToNode(hasContentDescription("Elimina"))
            compose.onNodeWithContentDescription("Elimina").performClick()
            compose.onNodeWithTag("extra_delete_confirm").performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            waitForTotal("1 h 00 min")
            compose.onNodeWithTag("next_month").performClick()
            waitForTotal("8 h 00 min")
        } finally {
            primary.close()
            secondary.close()
            runBlocking {
                dao.saveSettings(oldSettings)
                ids.forEach { dao.removeShift(it) }
                dao.findExtra(employee, month.atDay(8).toString(), "22:30", "01:00")?.let {
                    dao.removeExtra(it.id, employee)
                }
                dao.findExtra(employee, month.atDay(8).toString(), "22:00", "01:00")?.let {
                    dao.removeExtra(it.id, employee)
                }
                dao.findExtra(employee, month.minusMonths(1).atDay(3).toString(), "09:00", "10:15")
                    ?.let {
                        dao.removeExtra(it.id, employee)
                    }
                if (oldProfile != null) dao.saveProfile(oldProfile)
                else
                    app.database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM profiles WHERE id = ?",
                        arrayOf(employee),
                    )
                app.database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM weeks WHERE id = 'hours-test-week'"
                )
            }
        }
    }

    private fun waitForHome() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("home_import_actions").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTotal(text: String) {
        compose.waitUntil(15000) {
            compose
                .onAllNodes(hasTestTag("hours_total") and hasText(text))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("hours_total").assertTextEquals(text)
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, name).outputStream().use {
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
