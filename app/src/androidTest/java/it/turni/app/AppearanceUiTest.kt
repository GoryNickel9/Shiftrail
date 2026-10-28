package it.turni.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.ui.AppAppearance
import it.turni.app.ui.AppTheme
import it.turni.app.ui.AppViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppearanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun explicitThemesApplyImmediatelyPersistAndKeepTheBackground() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val appearance = AppAppearance(app)
        val previous = appearance.theme()
        val background =
            File(app.filesDir, "app-background.jpg").takeIf { it.exists() }?.readBytes()
        lateinit var vm: AppViewModel
        compose.runOnIdle { vm = ViewModelProvider(compose.activity)[AppViewModel::class.java] }
        compose.waitUntil(15000) { vm.state.value.loaded && !vm.state.value.busy }
        try {
            compose.onNodeWithText("Impostazioni").performClick()
            screenshot("settings")
            compose
                .onNode(hasScrollToIndexAction())
                .performScrollToNode(hasTestTag("menu_background"))
            compose.onNodeWithTag("menu_background").performClick()
            for (theme in listOf(AppTheme.DARK, AppTheme.LIGHT, AppTheme.SYSTEM)) {
                compose
                    .onNodeWithTag("theme_${theme.name.lowercase()}")
                    .performScrollTo()
                    .performClick()
                compose.waitUntil(15000) { vm.state.value.theme == theme && !vm.state.value.busy }
                assertEquals(theme, AppAppearance(app).theme())
                screenshot("background-${theme.name.lowercase()}")
                val store = ViewModelStore()
                try {
                    val restarted = AppViewModel(app)
                    store.put("appearance-restart", restarted)
                    assertEquals(theme, restarted.state.value.theme)
                } finally {
                    compose.runOnIdle { store.clear() }
                }
            }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("theme_system").assertIsSelected()
            val after = File(app.filesDir, "app-background.jpg").takeIf { it.exists() }?.readBytes()
            assertArrayEquals(background, after)
            compose.onNodeWithTag("settings_back").performClick()
            compose
                .onNode(hasScrollToIndexAction())
                .performScrollToNode(hasTestTag("menu_calendar"))
            compose.onNodeWithTag("menu_calendar").performClick()
            screenshot("calendar")
            compose.onNodeWithTag("settings_back").performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("menu_updates"))
            compose.onNodeWithTag("menu_updates").performClick()
            compose
                .onNodeWithText("Versione installata: ${BuildConfig.VERSION_NAME}")
                .assertIsDisplayed()
            screenshot("updates")
            compose.onNodeWithText("Home").performClick()
            screenshot("home")
            compose.onNodeWithText("Orario").performClick()
            screenshot("hours")
        } finally {
            runBlocking { appearance.save(previous) }
        }
    }

    private fun screenshot(name: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.getExternalFilesDir(null), "qa-1.0.2").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
