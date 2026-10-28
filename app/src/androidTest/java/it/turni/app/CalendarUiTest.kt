package it.turni.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import it.turni.app.calendar.WritableCalendar
import it.turni.app.ui.AppState
import it.turni.app.ui.CalendarScreen
import it.turni.app.ui.ResultScreen
import it.turni.app.ui.ShiftrailTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun completionShowsOnlyAddedCountAndDestination() {
        compose.setContent {
            ShiftrailTheme {
                ResultScreen(
                    AppState(
                        syncCount = 0,
                        syncedCalendar = WritableCalendar(11, "Turni", "worker@example.com"),
                    ),
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("Aggiunti nel calendario").assertIsDisplayed()
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithText("Calendario di destinazione").assertIsDisplayed()
        compose.onNodeWithText("Apri nel calendario").assertDoesNotExist()
        compose.onNodeWithText("Home").assertDoesNotExist()
    }

    @Test
    fun localDestinationIsExplicitBeforeSelection() {
        compose.setContent {
            ShiftrailTheme {
                CalendarScreen(
                    AppState(
                        calendars =
                            listOf(WritableCalendar(11, "Telefono", "Locale", localOnly = true))
                    ),
                    onChoose = { _, _ -> },
                    onRetry = {},
                )
            }
        }
        compose
            .onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Solo su questo dispositivo", substring = true))
        compose.onNodeWithText("Solo su questo dispositivo", substring = true).assertIsDisplayed()
    }

    @Test
    fun resultNamesDestinationAndDoesNotClaimCloudSync() {
        compose.setContent {
            ShiftrailTheme {
                ResultScreen(
                    AppState(
                        syncCount = 7,
                        syncedCalendar = WritableCalendar(11, "Turni", "worker@example.com"),
                    ),
                    onError = {},
                    onHome = {},
                )
            }
        }
        compose.onNodeWithText("Aggiunti nel calendario").assertIsDisplayed()
        compose.onNodeWithText("Turni").assertIsDisplayed()
        compose.onNodeWithText("worker@example.com").assertIsDisplayed()
        compose.onNodeWithText("7").assertIsDisplayed()
    }

    @Test
    fun calendarsWithSameNameCanBeChosenByAccount() {
        var chosen: Long? = null
        val state =
            AppState(
                calendars =
                    listOf(
                        WritableCalendar(11, "Turni", "first@example.com"),
                        WritableCalendar(12, "Turni", "second@example.com"),
                    )
            )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                ShiftrailTheme(darkTheme = true) {
                    CalendarScreen(
                        state,
                        onChoose = { id, enable ->
                            assertTrue(!enable)
                            chosen = id
                        },
                        onRetry = {},
                    )
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("first@example.com"))
        compose.onNodeWithText("first@example.com").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("second@example.com"))
        compose.onNodeWithTag("calendar_option_12").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(12L, chosen) }
    }

    @Test
    fun unavailableCalendarsExplainRecoveryAndAllowRetry() {
        var retried = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                ShiftrailTheme(darkTheme = false) {
                    CalendarScreen(AppState(), onChoose = { _, _ -> }, onRetry = { retried = true })
                }
            }
        }
        compose
            .onNodeWithText("Android non rende disponibile alcun calendario", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText("Aggiorna elenco")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun hiddenCalendarRequiresExplicitActivationAndReadOnlyCannotBeChosen() {
        var chosen: Pair<Long, Boolean>? = null
        compose.setContent {
            ShiftrailTheme {
                CalendarScreen(
                    AppState(
                        calendars =
                            listOf(
                                WritableCalendar(
                                    11,
                                    "Nascosto",
                                    "worker@example.com",
                                    visible = false,
                                    syncEnabled = false,
                                ),
                                WritableCalendar(
                                    12,
                                    "Festività",
                                    "worker@example.com",
                                    writable = false,
                                ),
                            )
                    ),
                    onChoose = { id, enable -> chosen = id to enable },
                    onRetry = {},
                )
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Festività"))
        compose.onNodeWithText("Festività").assertIsNotEnabled()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Nascosto"))
        compose.onNodeWithText("Nascosto").performScrollTo().performClick()
        compose.onNodeWithText("Attivare questo calendario?").assertIsDisplayed()
        compose.onNodeWithText("Annulla").performClick()
        compose.runOnIdle { assertEquals(null, chosen) }
        compose.onNodeWithText("Nascosto").performClick()
        compose.onNodeWithText("Attiva e usa").performClick()
        compose.runOnIdle { assertEquals(11L to true, chosen) }
    }
}
