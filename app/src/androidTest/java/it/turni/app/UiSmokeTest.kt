package it.turni.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
        compose.onNodeWithTag("menu_calendar").performScrollTo().performClick()
        scrollToSetting("work_color").performClick()
        compose.onNode(hasText("Viola") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("rest_color").assertDoesNotExist()
        compose.onNodeWithTag("save_rest_days").assertDoesNotExist()
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
        compose.onNodeWithTag("menu_calendar").performScrollTo().performClick()
        scrollToSetting("work_color")
        compose.onNodeWithText("Viola").assertIsDisplayed()
        compose.onNodeWithTag("rest_color").assertDoesNotExist()
        compose.onNodeWithTag("save_rest_days").assertDoesNotExist()
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
    }
}
