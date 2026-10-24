package it.turni.app

import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.calendar.WritableCalendar
import it.turni.app.data.ShiftEntity
import it.turni.app.ui.AppState
import it.turni.app.ui.CalendarScreen
import it.turni.app.ui.ResultScreen
import it.turni.app.ui.ShiftrailTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun savedEventOpensThroughDefaultCalendarIntent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val launched = AtomicReference<Intent>()
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action != Intent.ACTION_VIEW) return null
                    launched.set(intent)
                    return Instrumentation.ActivityResult(0, null)
                }
            }
        instrumentation.addMonitor(monitor)
        try {
            val shift =
                ShiftEntity(
                    "shift",
                    "week",
                    "employee",
                    "2026-11-02",
                    "09:00",
                    "17:00",
                    false,
                    null,
                    "WORK_SHIFT",
                    1.0,
                    42,
                    11,
                    "signature",
                    0,
                    0,
                )
            compose.setContent {
                ShiftrailTheme {
                    ResultScreen(
                        AppState(syncCount = 1, syncedShift = shift),
                        onError = { throw AssertionError(it) },
                        onHome = {},
                    )
                }
            }
            compose.onNodeWithText("Apri nel calendario").assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertEquals(
                    Uri.parse("content://com.android.calendar/events/42"),
                    launched.get().data,
                )
                assertEquals(null, launched.get().`package`)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
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
                    onChoose = {},
                    onRetry = {},
                )
            }
        }
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
        compose.onNodeWithText("Salvataggio nel calendario completato").assertIsDisplayed()
        compose.onNodeWithText("Turni").assertIsDisplayed()
        compose.onNodeWithText("worker@example.com").assertIsDisplayed()
        compose.onNodeWithText("Modifiche applicate: 7").assertIsDisplayed()
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
