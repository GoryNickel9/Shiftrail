package it.turni.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.data.*
import it.turni.app.payroll.*
import it.turni.core.*
import java.io.File
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PayrollUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun cardsShowDailyDifferencesSourceEditsPersistenceAndSafeDeletion() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val dao = app.database.dao()
        val oldProfile = runBlocking { dao.profile() }
        val oldSettings = runBlocking { dao.settings() } ?: SettingsEntity()
        val employee = "payroll-ui-test"
        val month = YearMonth.of(2026, 10)
        val pdf = PayrollFixture.pdf(app)
        val repository = PayrollRepository(app, app.database)
        var entity: PayrollReportEntity? = null
        CalendarFixture("payroll-ui-test").use { calendar ->
            try {
                runBlocking {
                    app.database.openHelper.writableDatabase.execSQL("DELETE FROM profiles")
                    dao.saveProfile(ProfileEntity(employee, "Luca Rossi", ""))
                    dao.saveSettings(
                        oldSettings.copy(calendarId = calendar.id, notificationsEnabled = false)
                    )
                    listOf(
                            Triple(1, "18:30", "21:30"),
                            Triple(2, "12:00", "15:30"),
                            Triple(28, "16:00", "23:00"),
                        )
                        .forEach { (day, start, end) ->
                            app.sync.saveExtra(
                                ExtraWorkEntity(
                                    "payroll-extra-$day",
                                    employee,
                                    month.atDay(day).toString(),
                                    start,
                                    end,
                                    1,
                                ),
                                null,
                                calendar.id,
                                EventOptions(),
                                ZoneId.systemDefault(),
                            )
                        }
                    val imported = repository.import(Uri.fromFile(pdf), employee, month) {}
                    val parsed = PayrollJson.decode(imported.payload)
                    entity =
                        repository.save(
                            imported,
                            parsed.copy(
                                identityConfirmed = true,
                                days =
                                    parsed.days.map {
                                        it.copy(reviewed = true, daylightSavingReviewed = true)
                                    },
                            ),
                        )
                }
                val originalEvents = runBlocking {
                    dao.allRecordedShifts(employee).first().associate {
                        it.calendarEventId to app.calendar.event(it.calendarEventId)
                    }
                }
                compose.runOnIdle { compose.activity.viewModelStore.clear() }
                compose.activityRule.scenario.recreate()
                compose.waitUntil(15000) {
                    compose.onAllNodesWithTag("tab_hours").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("tab_hours").performClick()
                val offset = ChronoUnit.MONTHS.between(month, YearMonth.now()).toInt()
                require(kotlin.math.abs(offset) < 24)
                repeat(kotlin.math.abs(offset)) {
                    compose
                        .onNodeWithTag(if (offset > 0) "previous_month" else "next_month")
                        .performClick()
                }
                compose.onNodeWithTag("hours_list").performScrollToNode(hasTestTag("open_payroll"))
                compose.onNodeWithTag("open_payroll").performClick()
                compose.waitUntil(15000) {
                    compose
                        .onAllNodesWithTag("payroll_app_total")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                compose
                    .onNodeWithTag("payroll_list")
                    .performScrollToNode(hasTestTag("payroll_day_2026-10-02"))
                screenshot(app, "payroll-cards.png")
                compose.onNodeWithTag("payroll_day_2026-10-02").performClick()
                compose
                    .onNodeWithTag("payroll_list")
                    .performScrollToNode(hasTestTag("payroll_show_source"))
                compose.onNodeWithTag("payroll_show_source").performClick()
                compose.waitUntil(15000) {
                    compose
                        .onAllNodesWithContentDescription("Documento originale")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                screenshot(app, "payroll-source.png")
                compose.onNodeWithTag("payroll_close_source").performClick()
                compose
                    .onNodeWithTag("payroll_list")
                    .performScrollToNode(hasTestTag("payroll_edit_detail"))
                compose.onNodeWithTag("payroll_edit_detail").performClick()
                compose.onNodeWithTag("payroll_edit_minutes").performTextClearance()
                compose.onNodeWithTag("payroll_edit_minutes").performTextInput("03:30")
                compose.onNodeWithTag("payroll_edit_stamps").performTextClearance()
                compose.onNodeWithTag("payroll_edit_stamps").performTextInput("E 12:00; U 15:30")
                compose.onNodeWithTag("payroll_save_row").assertIsNotEnabled()
                compose.onNodeWithTag("payroll_row_checked").performScrollTo().performClick()
                compose.onNodeWithTag("payroll_save_row").performClick()
                compose.waitUntil(15000) {
                    compose.onAllNodesWithTag("payroll_save_row").fetchSemanticsNodes().isEmpty()
                }
                val restored = runBlocking { dao.payrollReports(employee).first().single() }
                assertEquals(210, PayrollJson.decode(restored.payload).days[1].minutes)
                compose.activityRule.scenario.recreate()
                compose.waitUntil(15000) {
                    compose.onAllNodesWithTag("payroll_list").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("payroll_list").performScrollToIndex(0)
                compose.waitUntil(15000) {
                    compose
                        .onAllNodesWithTag("payroll_app_total")
                        .fetchSemanticsNodes()
                        .isNotEmpty() ||
                        compose
                            .onAllNodesWithTag("payroll_back_to_month")
                            .fetchSemanticsNodes()
                            .isNotEmpty()
                }
                if (
                    compose
                        .onAllNodesWithTag("payroll_back_to_month")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                ) {
                    compose
                        .onNodeWithTag("payroll_list")
                        .performScrollToNode(hasTestTag("payroll_back_to_month"))
                    compose.onNodeWithTag("payroll_back_to_month").performClick()
                }
                compose.onNodeWithTag("payroll_list").performScrollToIndex(0)
                compose
                    .onNodeWithTag("payroll_list")
                    .performScrollToNode(hasText(app.getString(R.string.payroll_total_mismatch)))
                compose
                    .onNodeWithText(app.getString(R.string.payroll_total_mismatch))
                    .assertExists()
                compose
                    .onNodeWithTag("payroll_list")
                    .performScrollToNode(hasTestTag("payroll_day_2026-10-25"))
                compose.onNodeWithTag("payroll_day_2026-10-25").performClick()
                compose
                    .onNodeWithTag("payroll_list")
                    .performScrollToNode(hasTestTag("payroll_edit_detail"))
                compose.onNodeWithTag("payroll_edit_detail").performClick()
                compose.onNodeWithTag("payroll_continue_1200").performScrollTo().performClick()
                compose.onNodeWithTag("payroll_row_checked").performScrollTo().performClick()
                compose.onNodeWithTag("payroll_save_row").performClick()
                compose.waitUntil(15000) {
                    compose.onAllNodesWithTag("payroll_save_row").fetchSemanticsNodes().isEmpty()
                }
                val continued = runBlocking {
                    PayrollJson.decode(dao.payrollReports(employee).first().single().payload)
                        .days[24]
                }
                assertTrue(
                    continued.punches.any {
                        it.kind == PunchKind.ENTRY && it.minute == 1200 && it.previousWorkDay
                    }
                )
                compose
                    .onNodeWithTag("payroll_list")
                    .performScrollToNode(hasTestTag("payroll_delete"))
                compose.onNodeWithTag("payroll_delete").performClick()
                compose.onNodeWithTag("payroll_delete_confirm").performClick()
                compose.waitUntil(15000) {
                    compose.onAllNodesWithTag("payroll_pick_pdf").fetchSemanticsNodes().isNotEmpty()
                }
                assertTrue(runBlocking { dao.payrollReports(employee).first() }.isEmpty())
                assertFalse(File(app.filesDir, "payroll/${entity!!.id}.pdf").exists())
                assertEquals(3, runBlocking { dao.allExtraWork(employee).first() }.size)
                originalEvents.forEach { (id, event) ->
                    assertEquals(event, runBlocking { app.calendar.event(id) })
                }
            } finally {
                runBlocking {
                    entity?.let { repository.delete(it) }
                    dao.allExtraWork(employee).first().forEach { dao.removeExtra(it.id, employee) }
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
                pdf.delete()
            }
        }
    }

    private fun screenshot(app: TurniApplication, name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(app.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
