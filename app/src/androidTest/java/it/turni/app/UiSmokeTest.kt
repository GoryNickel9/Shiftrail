package it.turni.app

import android.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
        assertSimplifiedHome()
        compose.onNodeWithText("Impostazioni").performClick()
        compose.onNodeWithText("Nome e cognome").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("Alias OCR, uno per riga").assertDoesNotExist()
        compose.onNodeWithText("Titolo evento").assertDoesNotExist()
        compose.onNodeWithText("Nome e cognome").performTextClearance()
        compose.onNodeWithText("Nome e cognome").performTextInput("Luca Baldeddu")
        scrollToSetting("reminder_setting").performClick()
        compose.onNode(hasText("1 ora prima") and hasAnyAncestor(isDialog())).performClick()
        scrollToSetting("work_color").performClick()
        compose.onNode(hasText("Viola") and hasAnyAncestor(isDialog())).performClick()
        scrollToSetting("rest_color").performClick()
        compose.onNode(hasText("Arancione") and hasAnyAncestor(isDialog())).performClick()
        val restSwitch = scrollToSetting("save_rest_days")
        if (
            restSwitch.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] ==
                ToggleableState.On
        )
            restSwitch.performClick()
        compose.onNodeWithText("Salva").assertIsDisplayed().performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodes(androidx.compose.ui.test.hasText("Importa per Luca Baldeddu"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Importa per Luca Baldeddu").assertIsDisplayed()
        assertSimplifiedHome()
        compose.onNodeWithText("Impostazioni").performClick()
        scrollToSetting("reminder_setting")
        compose.onNodeWithText("1 ora prima").assertIsDisplayed()
        scrollToSetting("work_color")
        compose.onNodeWithText("Viola").assertIsDisplayed()
        scrollToSetting("rest_color")
        compose.onNodeWithText("Arancione").assertIsDisplayed()
        scrollToSetting("save_rest_days").assertIsOff()
        compose.onNodeWithText("Home").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) {
            compose
                .onAllNodes(androidx.compose.ui.test.hasText("Importa per Luca Baldeddu"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Importa per Luca Baldeddu").assertIsDisplayed()
        assertSimplifiedHome()
    }

    private fun scrollToSetting(tag: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private fun assertSimplifiedHome() {
        compose.onNodeWithText("Storico").assertDoesNotExist()
        compose.onNodeWithText("Shiftrail").assertIsDisplayed()
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
