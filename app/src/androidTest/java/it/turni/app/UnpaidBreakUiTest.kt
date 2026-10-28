package it.turni.app

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.data.*
import it.turni.core.*
import java.io.File
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UnpaidBreakUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun breaksPersistReduceNightHoursValidateAndLeaveCalendarUntouched() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val dao = app.database.dao()
        val oldProfile = runBlocking { dao.profile() }
        val oldSettings = runBlocking { dao.settings() } ?: SettingsEntity()
        val employee = "break-ui-test"
        val date = YearMonth.now().atDay(15)
        val extra = ExtraWorkEntity("break-extra", employee, date.toString(), "22:00", "06:00", 1)
        CalendarFixture("unpaid-break-ui").use { fixture ->
            try {
                runBlocking {
                    if (oldProfile != null)
                        app.database.openHelper.writableDatabase.execSQL(
                            "DELETE FROM profiles WHERE id = ?",
                            arrayOf(oldProfile.id),
                        )
                    dao.saveProfile(ProfileEntity(employee, "Test pause", ""))
                    dao.saveSettings(
                        oldSettings.copy(calendarId = fixture.id, notificationsEnabled = false)
                    )
                    app.sync.saveExtra(
                        extra,
                        null,
                        fixture.id,
                        EventOptions(),
                        ZoneId.systemDefault(),
                    )
                }
                val shift = runBlocking { dao.allRecordedShifts(employee).first().single() }
                val original =
                    requireNotNull(runBlocking { app.calendar.event(shift.calendarEventId) })
                compose.runOnIdle { compose.activity.viewModelStore.clear() }
                compose.activityRule.scenario.recreate()
                compose.waitUntil(15000) {
                    compose.onAllNodesWithTag("tab_hours").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("tab_hours").performClick()
                total("8 h 00 min")
                compose
                    .onNodeWithTag("hours_list")
                    .performScrollToNode(hasTestTag("hours_date_${extra.id}"))
                val dateBounds =
                    compose.onNodeWithTag("hours_date_${extra.id}").getUnclippedBoundsInRoot()
                val editBounds =
                    compose.onNodeWithTag("edit_${extra.id}").getUnclippedBoundsInRoot()
                assertEquals(
                    (dateBounds.top + dateBounds.bottom).value / 2,
                    (editBounds.top + editBounds.bottom).value / 2,
                    1f,
                )
                add()
                choose("break_start", 2, 0)
                choose("break_end", 2, 30)
                screenshot(app, "break-editor.png")
                compose.onNodeWithTag("save_break").performClick()
                total("7 h 30 min")
                compose.onNodeWithTag("hours_night").assertTextEquals("5 h 30 min")
                val pause = runBlocking { dao.breaks(employee, extra.id).single() }
                assertEquals(date.plusDays(1).atTime(2, 0), pause.domain().start)
                compose
                    .onNodeWithTag("hours_list")
                    .performScrollToNode(hasTestTag("edit_break_${pause.id}"))
                compose.onNodeWithTag("edit_break_${pause.id}").performClick()
                choose("break_end", 3, 0)
                compose.onNodeWithTag("save_break").performClick()
                total("7 h 00 min")
                add()
                choose("break_start", 2, 30)
                choose("break_end", 3, 30)
                compose.onNodeWithTag("save_break").assertIsNotEnabled()
                compose
                    .onNodeWithTag("break_error")
                    .assertTextEquals("Le pause non possono sovrapporsi.")
                compose.onNodeWithTag("cancel_break").performClick()
                val rejected = runCatching {
                    runBlocking {
                        app.sync.saveExtra(
                            extra.copy(endTime = "02:00"),
                            extra,
                            fixture.id,
                            EventOptions(),
                            ZoneId.systemDefault(),
                        )
                    }
                }
                assertEquals("BREAK_OUTSIDE_SHIFT", rejected.exceptionOrNull()?.message)
                assertEquals(
                    original.start,
                    runBlocking { app.calendar.event(original.id) }!!.start,
                )
                assertEquals(original.end, runBlocking { app.calendar.event(original.id) }!!.end)
                compose.activityRule.scenario.recreate()
                total("7 h 00 min")
                screenshot(app, "break-saved.png")
                compose
                    .onNodeWithTag("hours_list")
                    .performScrollToNode(hasTestTag("delete_break_${pause.id}"))
                compose.onNodeWithTag("delete_break_${pause.id}").performClick()
                compose.onNodeWithTag("confirm_delete_break").performClick()
                total("8 h 00 min")
                runBlocking {
                    dao.saveBreak(pause)
                    app.sync.removeExtra(extra, EventOptions(), ZoneId.systemDefault())
                    assertTrue(dao.breaks(employee, extra.id).isEmpty())
                }
            } finally {
                runBlocking {
                    dao.removePeriodBreaks(extra.id)
                    dao.removeExtra(extra.id, employee)
                    app.database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM weeks WHERE employeeId = ?",
                        arrayOf(employee),
                    )
                    app.database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM profiles WHERE id = ?",
                        arrayOf(employee),
                    )
                    if (oldProfile != null) dao.saveProfile(oldProfile)
                    dao.saveSettings(oldSettings)
                }
            }
        }
    }

    private fun add() {
        compose.onNodeWithTag("hours_list").performScrollToNode(hasTestTag("add_break_break-extra"))
        compose.onNodeWithTag("add_break_break-extra").performClick()
    }

    private fun choose(tag: String, hour: Int, minute: Int) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.onNodeWithTag("time_hour").performSemanticsAction(SemanticsActions.SetProgress) {
            it(hour.toFloat())
        }
        compose.onNodeWithTag("time_minute").performSemanticsAction(SemanticsActions.SetProgress) {
            it((minute / 5).toFloat())
        }
        compose.onNodeWithTag("confirm_time").performClick()
    }

    private fun total(expected: String) {
        compose.onNodeWithTag("hours_list").performScrollToIndex(0)
        compose.waitUntil(15000) {
            compose
                .onAllNodes(hasTestTag("hours_total") and hasText(expected))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun screenshot(app: TurniApplication, name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val bitmap =
            requireNotNull(
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            )
        try {
            val dir = File(app.getExternalFilesDir(null), "qa-1.0.3").apply { mkdirs() }
            File(dir, name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
