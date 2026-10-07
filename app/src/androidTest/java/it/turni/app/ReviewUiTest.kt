package it.turni.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.ocr.ExtractedSchedule
import it.turni.app.ui.AppState
import it.turni.app.ui.AppViewModel
import it.turni.app.ui.ReviewScreen
import it.turni.core.*
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReviewUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun datesRecoveredCountersAndDisagreementsMustBeReviewedBeforeContinuing() {
        val monday = LocalDate.of(2026, 11, 16)
        val cells =
            listOf(
                ShiftParser.parse("21:15-03:45", monday, 0.65),
                ShiftParser.parse("20-01 / 20-03", monday.plusDays(1)),
            )
        val reviewedCells =
            listOf(
                cells[0].copy(reviewed = true),
                ShiftParser.parse("20-01", monday.plusDays(1)).copy(reviewed = true),
            )
        val table =
            ExtractionResult(
                selected = RowCandidate("NERI MARTA", Box(0.05f, 0.2f, 0.2f, 0.22f), 1.0),
                cells = reviewedCells,
                issues = setOf(ExtractionIssue.DATE_CONFIRMATION),
            )
        val state =
            mutableStateOf(AppState(extraction = ExtractedSchedule(emptyList(), table, false, 0)))
        val application =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val vm = AppViewModel(application)
        val store = ViewModelStore().apply { put("review-test", vm) }
        var continued = false
        try {
            compose.setContent {
                MaterialTheme {
                    ReviewScreen(state.value, vm, onCalendar = { continued = true }, onDebug = {})
                }
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Continua"))
            compose.onNodeWithText("Continua").assertIsNotEnabled()
            compose.runOnIdle {
                state.value =
                    state.value.copy(
                        confirmedMonday = monday,
                        extraction =
                            state.value.extraction!!.copy(
                                table =
                                    table.copy(
                                        issues = emptySet(),
                                        cells = listOf(cells[0], reviewedCells[1]),
                                    )
                            ),
                    )
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Continua"))
            compose.onNodeWithText("Continua").assertIsNotEnabled()
            compose.runOnIdle {
                state.value =
                    state.value.copy(
                        extraction =
                            state.value.extraction!!.copy(
                                table =
                                    table.copy(
                                        issues = emptySet(),
                                        cells = listOf(cells[0].copy(reviewed = true), cells[1]),
                                    )
                            )
                    )
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Continua"))
            compose.onNodeWithText("Continua").assertIsNotEnabled()
            compose.runOnIdle {
                val extraction = state.value.extraction!!
                state.value =
                    state.value.copy(
                        extraction =
                            extraction.copy(table = extraction.table.copy(cells = reviewedCells))
                    )
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Continua"))
            compose.onNodeWithText("Continua").assertIsEnabled().performClick()
            compose.runOnIdle { assertTrue(continued) }
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }
}
