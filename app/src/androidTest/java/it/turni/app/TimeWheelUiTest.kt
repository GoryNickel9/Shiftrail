package it.turni.app

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import it.turni.app.ui.ShiftrailTheme
import it.turni.app.ui.TimeWheelDialog
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimeWheelUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun wheelsOfferOnlyQuarterHoursAndRequireExplicitConfirmation() {
        var saved = LocalTime.of(8, 7)
        compose.runOnUiThread {
            compose.activity.setContent {
                ShiftrailTheme { TimeWheelDialog(saved, {}, { saved = it }) }
            }
        }
        compose.onNodeWithTag("time_hour").performSemanticsAction(SemanticsActions.SetProgress) {
            it(23f)
        }
        compose.onNodeWithTag("time_minute").performSemanticsAction(SemanticsActions.SetProgress) {
            it(3f)
        }
        assertEquals(LocalTime.of(8, 7), saved)
        compose.onNodeWithTag("confirm_time").performClick()
        assertEquals(LocalTime.of(23, 45), saved)
    }

    @Test
    fun cancellingDoesNotRoundAnExistingTime() {
        var saved = LocalTime.of(8, 7)
        var cancelled = false
        compose.runOnUiThread {
            compose.activity.setContent {
                ShiftrailTheme { TimeWheelDialog(saved, { cancelled = true }, { saved = it }) }
            }
        }
        compose.onNodeWithText("Annulla").performClick()
        assertTrue(cancelled)
        assertEquals(LocalTime.of(8, 7), saved)
    }

    @Test
    fun minuteWheelRespondsToRealSwipeAndSnapsToQuarterHours() {
        var saved = LocalTime.of(8, 7)
        compose.runOnUiThread {
            compose.activity.setContent {
                ShiftrailTheme { TimeWheelDialog(saved, {}, { saved = it }) }
            }
        }
        compose.onNodeWithTag("time_minute").performTouchInput { swipeUp(durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithTag("confirm_time").performClick()
        assertTrue(saved.minute in listOf(15, 30, 45))
    }
}
