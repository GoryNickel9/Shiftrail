package it.turni.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.ui.AppViewModel
import it.turni.app.ui.TurniApp
import it.turni.core.ReleaseUpdate
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BetaUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun optionalBetaAllowsNavigationAndCanBeLeftWithoutDownloading() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val preferences = app.getSharedPreferences("updater", 0)
        val oldRequired = preferences.getString("required_release", null)
        val oldBeta = preferences.getString("beta_release", null)
        val oldEnabled = app.updater.betaEnabled()
        preferences.edit().remove("required_release").commit()
        app.updater.setBetaEnabled(true)
        val beta = ReleaseUpdate(BuildConfig.VERSION_CODE + 1, "99.0.0-beta.1", "a".repeat(64), 26)
        app.updater.rememberUpdate(beta)
        val vm = AppViewModel(app)
        val store = ViewModelStore().apply { put("beta", vm) }
        try {
            compose.runOnUiThread { compose.activity.setContent { TurniApp(vm) } }
            compose.waitUntil(15000) { vm.state.value.loaded && !vm.state.value.busy }
            compose.onNodeWithTag("required_update").assertDoesNotExist()
            compose.onNodeWithText("Ore").assertIsDisplayed()
            compose.onNodeWithText("Impostazioni").performClick()
            compose.onNodeWithTag("menu_updates").performScrollTo().performClick()
            compose
                .onNode(hasScrollToIndexAction())
                .performScrollToNode(hasTestTag("beta_available"))
            compose.onNodeWithText("Beta disponibile: Shiftrail 99.0.0-beta.1").assertExists()
            compose.onNodeWithText("Scarica beta").assertExists()
            File(app.filesDir, "shiftrail-beta-settings.png").outputStream().use {
                compose
                    .onRoot()
                    .captureToImage()
                    .asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithTag("beta_enabled").performScrollTo().performClick()
            compose.waitUntil(5000) { !vm.state.value.updater.betaEnabled }
            assertNull(app.updater.betaUpdate())
            compose.onNodeWithTag("beta_available").assertDoesNotExist()
            compose.onNodeWithTag("required_update").assertDoesNotExist()
        } finally {
            compose.runOnIdle { store.clear() }
            preferences
                .edit()
                .putString("required_release", oldRequired)
                .putString("beta_release", oldBeta)
                .putBoolean("beta_enabled", oldEnabled)
                .commit()
        }
    }
}
