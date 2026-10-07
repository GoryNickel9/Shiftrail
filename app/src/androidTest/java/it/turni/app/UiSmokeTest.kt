package it.turni.app

import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeSettingsAndProfileFields() {
        compose.waitUntil(20000) {
            compose
                .onAllNodes(androidx.compose.ui.test.hasText("Impostazioni"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertArtworkVisibleWithoutScrolling()
        compose.onNodeWithText("Impostazioni").performClick()
        compose.onNodeWithText("Nome e cognome da cercare").assertIsDisplayed()
        compose.onNodeWithText("Alias OCR, uno per riga").assertIsDisplayed()
        compose.onNodeWithText("Nome e cognome da cercare").performTextClearance()
        compose.onNodeWithText("Nome e cognome da cercare").performTextInput("Luca Baldeddu")
        compose.onNodeWithText("Alias OCR, uno per riga").performTextClearance()
        compose.onNodeWithText("Alias OCR, uno per riga").performTextInput("BALDEDDU")
        compose.onNodeWithText("Salva").assertIsDisplayed().performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodes(androidx.compose.ui.test.hasText("Importa per Luca Baldeddu"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Importa per Luca Baldeddu").assertIsDisplayed()
        assertArtworkVisibleWithoutScrolling()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) {
            compose
                .onAllNodes(androidx.compose.ui.test.hasText("Importa per Luca Baldeddu"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Importa per Luca Baldeddu").assertIsDisplayed()
        assertArtworkVisibleWithoutScrolling()
    }

    private fun assertArtworkVisibleWithoutScrolling() {
        // Inspect the real Android framebuffer: an Image node can exist while nothing is drawn.
        compose.onNodeWithTag("home_illustration").assertIsDisplayed()
        compose.waitUntil(15000) {
            val bounds =
                compose.onNodeWithTag("home_illustration").fetchSemanticsNode().boundsInWindow
            val screenshot =
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    ?: return@waitUntil false
            var bluePixels = 0
            try {
                for (y in
                    bounds.top.toInt().coerceAtLeast(0) until
                        bounds.bottom.toInt().coerceAtMost(screenshot.height) step
                        8) {
                    for (x in
                        bounds.left.toInt().coerceAtLeast(0) until
                            bounds.right.toInt().coerceAtMost(screenshot.width) step
                            8) {
                        val pixel = screenshot.getPixel(x, y)
                        if (
                            Color.blue(pixel) > Color.red(pixel) + 40 &&
                                Color.blue(pixel) > Color.green(pixel) + 20
                        )
                            bluePixels++
                    }
                }
            } finally {
                screenshot.recycle()
            }
            bluePixels > 30
        }
    }
}
