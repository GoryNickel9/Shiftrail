package it.turni.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.ui.AppViewModel
import it.turni.app.ui.TurniApp
import it.turni.core.ReleaseUpdate
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RequiredUpdateUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun cachedUpdateBlocksNavigationAndBackEvenWithoutANetworkCheck() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val preferences = app.getSharedPreferences("updater", 0)
        val previous = preferences.getString("required_release", null)
        val update = ReleaseUpdate(BuildConfig.VERSION_CODE + 1, "99.0.0", "a".repeat(64), 26)
        preferences.edit().remove("required_release").commit()
        app.updater.rememberUpdate(update)
        val vm = AppViewModel(app)
        val store = ViewModelStore().apply { put("required-update", vm) }
        try {
            compose.setContent {
                val density = LocalDensity.current
                val configuration =
                    Configuration(LocalConfiguration.current).apply {
                        uiMode =
                            (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                                Configuration.UI_MODE_NIGHT_YES
                    }
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, 1.6f),
                    LocalConfiguration provides configuration,
                ) {
                    TurniApp(vm)
                }
            }
            compose.waitUntil(15000) { vm.state.value.loaded && !vm.state.value.busy }
            compose.onNodeWithText("Aggiornamento obbligatorio").assertIsDisplayed()
            compose.onNodeWithText("Scarica aggiornamento").assertIsDisplayed()
            compose.onNodeWithText("Più tardi").assertDoesNotExist()
            compose.onNodeWithText("Home").assertDoesNotExist()
            compose.onNodeWithText("Ore").assertDoesNotExist()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("required_update").assertIsDisplayed()
            val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
            assertEquals(255, android.graphics.Color.alpha(screenshot.getPixel(4, 4)))
            org.junit.Assert.assertTrue(
                android.graphics.Color.luminance(screenshot.getPixel(4, 4)) < 0.1f
            )
            File(app.getExternalFilesDir(null), "qa-required-update.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            assertEquals(update, vm.state.value.updater.release)
        } finally {
            compose.runOnIdle { store.clear() }
            preferences.edit().putString("required_release", previous).commit()
        }
    }
}
