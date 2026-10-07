package it.turni.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        compose.onNodeWithText("Impostazioni").performClick()
        compose.onNodeWithText("Nome e cognome da cercare").assertIsDisplayed()
        compose.onNodeWithText("Alias OCR, uno per riga").assertIsDisplayed()
        compose.onNodeWithText("Nome e cognome da cercare").performTextClearance()
        compose.onNodeWithText("Nome e cognome da cercare").performTextInput("Luca Baldeddu")
        compose.onNodeWithText("Alias OCR, uno per riga").performTextClearance()
        compose.onNodeWithText("Alias OCR, uno per riga").performTextInput("BALDEDDU")
        compose.onNodeWithText("Salva").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodes(androidx.compose.ui.test.hasText("Luca Baldeddu"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Luca Baldeddu").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) {
            compose
                .onAllNodes(androidx.compose.ui.test.hasText("Luca Baldeddu"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Luca Baldeddu").assertIsDisplayed()
    }
}
