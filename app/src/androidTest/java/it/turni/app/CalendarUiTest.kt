package it.turni.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import it.turni.app.calendar.WritableCalendar
import it.turni.app.ui.AppState
import it.turni.app.ui.CalendarScreen
import it.turni.app.ui.ShiftrailTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarUiTest {
    @get:Rule val compose = createComposeRule()

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
                    CalendarScreen(state, onChoose = { chosen = it }, onRetry = {})
                }
            }
        }
        compose.onNodeWithText("first@example.com").assertIsDisplayed()
        compose.onNodeWithText("second@example.com").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(12L, chosen) }
    }

    @Test
    fun unavailableCalendarsExplainRecoveryAndAllowRetry() {
        var retried = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                ShiftrailTheme(darkTheme = false) {
                    CalendarScreen(AppState(), onChoose = {}, onRetry = { retried = true })
                }
            }
        }
        compose
            .onNodeWithText("Nessun calendario visibile e scrivibile", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Riprova").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(retried) }
    }
}
