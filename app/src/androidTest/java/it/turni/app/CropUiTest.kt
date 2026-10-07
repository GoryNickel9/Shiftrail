package it.turni.app

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import it.turni.app.ui.CropScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CropUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tallPhotoKeepsAnalyzeButtonVisible() {
        val bitmap = Bitmap.createBitmap(128, 512, Bitmap.Config.ARGB_8888)
        var analyzed = false
        compose.setContent {
            MaterialTheme { CropScreen(bitmap, onAnalyze = { analyzed = true }, onError = {}) }
        }
        compose.onNodeWithText("Analizza foto").assertIsDisplayed().performClick()
        assertTrue(analyzed)
    }
}
