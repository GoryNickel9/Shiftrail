package it.turni.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.calendar.WritableCalendar
import it.turni.app.data.SettingsEntity
import it.turni.app.ui.*
import it.turni.core.*
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Release101UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun accountIsShownOnceAndViewerIsNotGuessedFromGoogleAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread {
            compose.activity.setContent {
                ShiftrailTheme {
                    CalendarScreen(
                        AppState(
                            calendars =
                                listOf(
                                    WritableCalendar(
                                        1,
                                        "worker@example.com",
                                        "worker@example.com",
                                        accountType = "com.google",
                                    ),
                                    WritableCalendar(
                                        2,
                                        "Turni",
                                        "worker@example.com",
                                        accountType = "com.google",
                                    ),
                                    WritableCalendar(3, "PC Sync", "PC Sync", localOnly = true),
                                )
                        ),
                        { _, _ -> },
                        {},
                    )
                }
            }
        }
        compose.onAllNodesWithText("worker@example.com").assertCountEquals(1)
        compose.onNodeWithText("Calendario principale").assertExists()
        compose.onNodeWithText("Google").assertExists()
        compose.onNodeWithText(calendarViewer(context) ?: "Da scegliere quando apri").assertExists()
        screenshot("calendars")
    }

    @Test
    fun weekDraftIsCancelledOrAppliedExplicitlyAndClipsTheMonth() {
        var open by mutableStateOf(true)
        var selected by mutableIntStateOf(-1)
        val periods =
            listOf(
                WorkPeriod(
                    "oct",
                    LocalDate.of(2026, 10, 26),
                    LocalTime.of(6, 0),
                    LocalTime.of(14, 0),
                    WorkSource.SCHEDULE,
                ),
                WorkPeriod(
                    "extra",
                    LocalDate.of(2026, 10, 29),
                    LocalTime.of(14, 0),
                    LocalTime.of(16, 0),
                    WorkSource.EXTRA,
                ),
                WorkPeriod(
                    "nov",
                    LocalDate.of(2026, 11, 1),
                    LocalTime.of(6, 0),
                    LocalTime.of(14, 0),
                    WorkSource.SCHEDULE,
                ),
            )
        compose.runOnUiThread {
            compose.activity.setContent {
                ShiftrailTheme {
                    Column(Modifier.fillMaxSize()) {
                        Text("Periodo $selected")
                        Button(onClick = { open = true }) { Text("Scegli") }
                        if (open)
                            WeekPicker(
                                YearMonth.of(2026, 10),
                                selected,
                                periods,
                                { open = false },
                            ) {
                                selected = it
                                open = false
                            }
                    }
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("week_option_1"))
        compose.onNodeWithTag("week_option_1").performClick()
        assertEquals(-1, selected)
        compose.onNodeWithTag("close_week_picker").performClick()
        compose.onNodeWithText("Periodo -1").assertIsDisplayed()
        compose.onNodeWithText("Scegli", useUnmergedTree = true).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("week_option_4"))
        compose.onNodeWithTag("week_option_4").performClick()
        compose
            .onNodeWithTag("week_hours_preview")
            .assertTextContains("10 h 00 min", substring = true)
        screenshot("week-picker")
        compose.onNodeWithText("Mostra settimana").performClick()
        compose.onNodeWithText("Periodo 4").assertIsDisplayed()
    }

    @Test
    fun legacyPreferenceCannotEnableRestImports() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val dao = app.database.dao()
        val previous = runBlocking { dao.settings() } ?: SettingsEntity()
        val store = ViewModelStore()
        try {
            runBlocking { dao.saveSettings(previous.copy(saveRestDays = true)) }
            val vm = AppViewModel(app)
            compose.runOnIdle { store.put("rest-policy", vm) }
            compose.waitUntil(15000) { vm.state.value.loaded && !vm.state.value.busy }
            assertFalse(vm.state.value.settings.saveRestDays)
        } finally {
            compose.runOnIdle { store.clear() }
            runBlocking { dao.saveSettings(previous) }
        }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "qa-1.0.1").apply { mkdirs() }
        val image =
            requireNotNull(
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            )
        try {
            File(directory, "$name.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            image.recycle()
        }
    }
}
