package it.turni.app

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.data.SettingsEntity
import it.turni.app.ui.AppViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PersonalizationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun backgroundAndCustomReminderPreferencesSurviveActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as TurniApplication
        val dao = app.database.dao()
        val oldSettings = runBlocking { dao.settings() } ?: SettingsEntity()
        val oldProfile = runBlocking { dao.profile() }
        val backgroundFile = File(app.filesDir, "app-background.jpg")
        val oldBackground = backgroundFile.takeIf { it.exists() }?.readBytes()
        val image = File(app.cacheDir, "personalization-test.png")
        val bitmap =
            Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        if (Build.VERSION.SDK_INT >= 33)
            instrumentation.uiAutomation.grantRuntimePermission(
                app.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        try {
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("Impostazioni").fetchSemanticsNodes().isNotEmpty()
            }
            lateinit var readyViewModel: AppViewModel
            compose.runOnIdle {
                readyViewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]
            }
            compose.waitUntil(15000) {
                readyViewModel.state.value.loaded && !readyViewModel.state.value.busy
            }
            compose.onNodeWithText("Impostazioni").performClick()
            if (oldProfile == null)
                compose.onNodeWithText("Nome e cognome").performTextInput("Test Personalizzazione")
            compose.runOnIdle {
                ViewModelProvider(compose.activity)[AppViewModel::class.java].setBackground(
                    Uri.fromFile(image)
                )
            }
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("app_background").fetchSemanticsNodes().isNotEmpty()
            }
            compose
                .onNodeWithTag("settings_list")
                .performScrollToNode(hasTestTag("menu_notifications"))
            compose.onNodeWithTag("menu_notifications").performClick()
            compose
                .onNodeWithTag("settings_list")
                .performScrollToNode(hasTestTag("notifications_enabled"))
            if (!oldSettings.notificationsEnabled)
                compose.onNodeWithTag("notifications_enabled").performClick()
            compose.onNodeWithTag("notification_hours").performScrollTo().performSemanticsAction(
                SemanticsActions.SetProgress
            ) {
                it(24f)
            }
            compose.onNodeWithTag("notification_minutes").performSemanticsAction(
                SemanticsActions.SetProgress
            ) {
                it(1f)
            }
            compose.onNodeWithText("Salva").assertIsNotEnabled()
            compose.onNodeWithTag("notification_minutes").performSemanticsAction(
                SemanticsActions.SetProgress
            ) {
                it(0f)
            }
            compose.onNodeWithText("Salva").assertIsEnabled()
            compose.onNodeWithTag("notification_hours").performScrollTo().performSemanticsAction(
                SemanticsActions.SetProgress
            ) {
                it(1f)
            }
            compose.onNodeWithTag("notification_minutes").performSemanticsAction(
                SemanticsActions.SetProgress
            ) {
                it(3f)
            }
            if (oldSettings.notificationSound)
                compose.onNodeWithTag("notification_sound").performScrollTo().performClick()
            if (oldSettings.notificationVibration)
                compose.onNodeWithTag("notification_vibration").performScrollTo().performClick()
            File(app.filesDir, "qa-personalization.png").outputStream().use {
                compose
                    .onRoot()
                    .captureToImage()
                    .asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.activityRule.scenario.recreate()
            compose
                .onNodeWithTag("notification_hours")
                .performScrollTo()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.ProgressBarRangeInfo,
                        ProgressBarRangeInfo(1f, 0f..24f, 23),
                    )
                )
            compose.onNodeWithText("Salva").performClick()
            compose.waitUntil(15000) { runBlocking { dao.settings()?.notificationMinutes == 75 } }
            val settings = requireNotNull(runBlocking { dao.settings() })
            assertTrue(settings.notificationsEnabled)
            assertFalse(settings.notificationSound)
            assertFalse(settings.notificationVibration)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("app_background").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Impostazioni").performClick()
            compose
                .onNodeWithTag("settings_list")
                .performScrollToNode(hasTestTag("menu_notifications"))
            compose.onNodeWithTag("menu_notifications").performClick()
            compose
                .onNodeWithTag("settings_list")
                .performScrollToNode(hasTestTag("notification_minutes"))
            compose
                .onNodeWithTag("notification_minutes")
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.ProgressBarRangeInfo,
                        ProgressBarRangeInfo(3f, 0f..11f, 10),
                    )
                )
            compose.onNodeWithTag("tab_settings").performClick()
            compose
                .onNodeWithTag("settings_list")
                .performScrollToNode(hasTestTag("menu_background"))
            compose.onNodeWithTag("menu_background").performClick()
            compose
                .onNodeWithTag("settings_list")
                .performScrollToNode(hasTestTag("reset_background"))
            compose.onNodeWithTag("reset_background").performClick()
            compose.waitUntil(15000) { !backgroundFile.exists() }
            compose.onNodeWithTag("app_background").assertDoesNotExist()
        } finally {
            runBlocking {
                dao.saveSettings(oldSettings)
                if (oldProfile == null)
                    app.database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM profiles WHERE name = 'Test Personalizzazione'"
                    )
            }
            if (oldBackground == null) backgroundFile.delete()
            else backgroundFile.writeBytes(oldBackground)
            image.delete()
        }
    }
}
