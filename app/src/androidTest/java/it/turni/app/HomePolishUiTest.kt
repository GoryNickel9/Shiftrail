package it.turni.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.data.*
import it.turni.app.ui.*
import java.io.File
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test

class HomePolishUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeDropsStartedShiftsWhileOpenAndOmitsRemovedTexts() {
        val now = LocalDateTime.now().withNano(0)
        fun shift(id: String, start: LocalDateTime) =
            ShiftEntity(
                id,
                "week",
                "profile",
                start.toLocalDate().toString(),
                start.toLocalTime().toString(),
                start.plusHours(3).toLocalTime().toString(),
                start.plusHours(3).toLocalDate() > start.toLocalDate(),
                null,
                "WORK_SHIFT",
                1.0,
                1,
                1,
                "",
                1,
                1,
            )
        val state =
            AppState(
                loaded = true,
                profile = ProfileEntity("profile", "Demo", ""),
                upcoming =
                    listOf(
                        shift("future", now.plusHours(2).withSecond(0)),
                        shift("current", now.minusHours(1)),
                        shift("soon", now.plusSeconds(8)),
                    ),
            )
        compose.runOnUiThread {
            compose.activity.setContent { ShiftrailTheme { HomeScreen(state, {}, {}, {}) } }
        }
        compose
            .onNode(
                hasTestTag("home_shift_soon") and hasAnyAncestor(hasTestTag("home_illustration"))
            )
            .assertIsDisplayed()
        compose.onNodeWithTag("home_shift_current").assertDoesNotExist()
        compose.onNodeWithText("Dalla foto al calendario.").assertDoesNotExist()
        compose.onNodeWithText("Importa per Demo").assertDoesNotExist()
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("home_shift_soon").fetchSemanticsNodes().isEmpty()
        }
        compose
            .onNode(
                hasTestTag("home_shift_future") and hasAnyAncestor(hasTestTag("home_illustration"))
            )
            .assertIsDisplayed()
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap =
            requireNotNull(
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            )
        try {
            val dir = File(app.getExternalFilesDir(null), "qa-1.0.4").apply { mkdirs() }
            File(dir, "home.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
