package it.turni.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import it.turni.app.ui.EditCellDialog
import it.turni.app.ui.ShiftrailTheme
import it.turni.core.ShiftParser
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReviewTimeWheelUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun newShiftRequiresTimesAndSavesAnOvernightIntervalFromWheels() {
        val date = LocalDate.of(2026, 10, 26)
        var saved: String? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                ShiftrailTheme {
                    EditCellDialog(
                        null,
                        date,
                        {},
                        { _, raw ->
                            saved = raw
                            true
                        },
                    )
                }
            }
        }
        compose.onNodeWithText("Salva").performClick()
        assertNull(saved)
        compose.selectTime("review_start", "22:15")
        compose.selectTime("review_end", "01:45")
        compose.onNodeWithText("Salva").performClick()
        assertEquals("22:15-01:45", saved)
        val cell = ShiftParser.parse(requireNotNull(saved), date)
        assertTrue(cell.endNextDay)
    }

    @Test
    fun cancellingWheelPreservesOcrMinutesAndRestOrEmptyNeedNoTimeInput() {
        val date = LocalDate.of(2026, 10, 26)
        val cell = ShiftParser.parse("14:07-21:23", date)
        var saved: String? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                ShiftrailTheme {
                    EditCellDialog(
                        cell,
                        date,
                        {},
                        { _, raw ->
                            saved = raw
                            true
                        },
                    )
                }
            }
        }
        compose.scrollToDialogControl("review_start").performClick()
        compose.onNodeWithTag("cancel_time").performClick()
        compose.onNodeWithText("Salva").performClick()
        assertEquals("14:07-21:23", saved)
        compose.scrollToDialogControl("review_type_CODE").performClick()
        compose.onNodeWithTag("review_start").assertDoesNotExist()
        compose.onNodeWithText("Salva").performClick()
        assertEquals("X", saved)
        compose.scrollToDialogControl("review_type_EMPTY").performClick()
        compose.onNodeWithText("Salva").performClick()
        assertEquals("", saved)
    }
}
