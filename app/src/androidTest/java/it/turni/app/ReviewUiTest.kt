package it.turni.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.ocr.ExtractedSchedule
import it.turni.app.ui.AppState
import it.turni.app.ui.AppViewModel
import it.turni.app.ui.ReviewScreen
import it.turni.app.ui.ShiftrailTheme
import it.turni.core.*
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReviewUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun xIsRestAndOtherLettersRequireCorrection() {
        val date = LocalDate.of(2026, 11, 16)
        val table =
            ExtractionResult(
                selected = RowCandidate("NERI MARTA", Box(0f, 0f, 0.2f, 0.1f), 1.0),
                cells = listOf(ShiftParser.parse("X", date, 0.90)),
            )
        val state =
            mutableStateOf(AppState(extraction = ExtractedSchedule(emptyList(), table, false, 0)))
        val application =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val vm = AppViewModel(application)
        val store = ViewModelStore().apply { put("rest-test", vm) }
        try {
            compose.setContent {
                ShiftrailTheme(darkTheme = false) { ReviewScreen(state.value, vm, {}) }
            }
            compose.onNodeWithText("Giorno di riposo").assertIsDisplayed()
            compose.onNodeWithText("Ho controllato: è corretto").assertDoesNotExist()
            compose.onNodeWithText("Continua").assertIsEnabled()
            compose.runOnIdle {
                state.value =
                    state.value.copy(
                        extraction =
                            state.value.extraction!!.copy(
                                table =
                                    table.copy(cells = listOf(ShiftParser.parse("FE", date, 1.0)))
                            )
                    )
            }
            compose.onNodeWithText("Da correggere").assertIsDisplayed()
            compose.onNodeWithText("Ho controllato: è corretto").assertDoesNotExist()
            compose.onNodeWithText("Continua").assertIsNotEnabled()
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }

    @Test
    fun ninetyAndAboveContinueWithoutCellConfirmation() {
        val date = LocalDate.of(2026, 11, 16)
        val table =
            ExtractionResult(
                selected = RowCandidate("NERI MARTA", Box(0f, 0f, 0.2f, 0.1f), 1.0),
                cells = (0..6).map { ShiftParser.parse("22-1", date.plusDays(it.toLong()), 0.90) },
            )
        val state =
            mutableStateOf(AppState(extraction = ExtractedSchedule(emptyList(), table, false, 0)))
        val application =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val vm = AppViewModel(application)
        val store = ViewModelStore().apply { put("threshold-test", vm) }
        var continued = false
        try {
            compose.setContent {
                ShiftrailTheme(darkTheme = false) {
                    ReviewScreen(state.value, vm, { continued = true })
                }
            }
            compose.onNodeWithText("Ho controllato: è corretto").assertDoesNotExist()
            compose.onNodeWithText("Continua").assertIsDisplayed().assertIsEnabled().performClick()
            compose.runOnIdle { assertTrue(continued) }
            saveScreenshot("review-ready.png")
            compose.runOnIdle {
                state.value =
                    state.value.copy(
                        extraction =
                            state.value.extraction!!.copy(
                                table =
                                    table.copy(
                                        cells =
                                            listOf(table.cells.first().copy(confidence = 0.899999))
                                    )
                            )
                    )
            }
            compose.onNodeWithText("Continua").assertIsNotEnabled()
            compose.onNodeWithText("Ho controllato: è corretto").assertExists()
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }

    @Test
    fun reviewFooterAndWrappedActionsRemainUsableWithLargeFonts() {
        val date = LocalDate.of(2026, 11, 16)
        val table =
            ExtractionResult(
                selected = RowCandidate("NERI MARTA", Box(0f, 0f, 0.2f, 0.1f), 1.0),
                cells =
                    (0..6).map {
                        ShiftParser.parse("18:30-21:30", date.plusDays(it.toLong()), 0.79)
                            .copy(source = Box(0.2f, 0.2f, 0.8f, 0.4f))
                    },
            )
        val state =
            AppState(
                extraction = ExtractedSchedule(emptyList(), table, false, 0),
                analyzed = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888),
            )
        val application =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val vm = AppViewModel(application)
        val store = ViewModelStore().apply { put("large-font-test", vm) }
        try {
            compose.setContent {
                ShiftrailTheme(darkTheme = false) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                        Box(Modifier.width(320.dp)) { ReviewScreen(state, vm, {}) }
                    }
                }
            }
            compose.onNodeWithText("Continua").assertIsDisplayed().assertIsNotEnabled()
            compose
                .onNode(hasScrollToIndexAction())
                .performScrollToNode(hasText("Ho controllato: è corretto"))
            compose.onAllNodesWithText("Ho controllato: è corretto")[0].assertIsDisplayed()
            compose.onAllNodesWithText("Modifica")[0].assertIsDisplayed()
            compose.onAllNodesWithText("Mostra origine")[0].assertIsDisplayed()
            saveScreenshot("review-large-font.png")
        } finally {
            compose.runOnIdle { store.clear() }
        }
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

    @Test
    fun datesRecoveredCountersAndDisagreementsMustBeReviewedBeforeContinuing() {
        val monday = LocalDate.of(2026, 11, 16)
        val cells =
            listOf(
                ShiftParser.parse("21:15-03:45", monday, 0.65).copy(ocrOriginal = "4 21I:15-03:45"),
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
                ShiftrailTheme {
                    ReviewScreen(state.value, vm, onCalendar = { continued = true })
                }
            }
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
            compose.onNodeWithText("Continua").assertIsNotEnabled()
            compose
                .onNode(hasScrollToIndexAction())
                .performScrollToNode(hasText("Lettura OCR originale: 4 21I:15-03:45"))
            compose.onNodeWithText("Lettura OCR originale: 4 21I:15-03:45").assertExists()
            compose.onNodeWithText("Punteggio lettura: 65/100").assertExists()
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
            compose.onNodeWithText("Continua").assertIsNotEnabled()
            compose.runOnIdle {
                val extraction = state.value.extraction!!
                state.value =
                    state.value.copy(
                        extraction =
                            extraction.copy(table = extraction.table.copy(cells = reviewedCells))
                    )
            }
            compose.onNodeWithText("Continua").assertIsEnabled().performClick()
            compose.runOnIdle { assertTrue(continued) }
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }
}
