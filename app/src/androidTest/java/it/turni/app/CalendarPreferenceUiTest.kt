package it.turni.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.ui.AppViewModel
import it.turni.app.ui.TurniApp
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CalendarPreferenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun destinationCanBeChosenInSettingsAndSurvivesANewViewModel() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val oldSettings = runBlocking { app.database.dao().settings() }
        CalendarFixture("preferred-other").use {
            CalendarFixture("preferred-target").use { target ->
                val model = mutableStateOf(AppViewModel(app))
                val store = ViewModelStore().apply { put("preferences", model.value) }
                try {
                    compose.runOnUiThread { compose.activity.setContent { TurniApp(model.value) } }
                    compose.waitUntil(15000) {
                        model.value.state.value.loaded && !model.value.state.value.busy
                    }
                    compose.onNodeWithText("Impostazioni").performClick()
                    compose.onNodeWithTag("menu_calendar").performClick()
                    compose
                        .onNode(hasScrollToIndexAction())
                        .performScrollToNode(hasTestTag("destination_calendar"))
                    compose.onNodeWithTag("destination_calendar").performClick()
                    compose
                        .onNode(hasScrollToIndexAction())
                        .performScrollToNode(hasText("preferred-target"))
                    compose.onNodeWithText("preferred-target").performClick()
                    compose.waitUntil(15000) {
                        model.value.state.value.settings.calendarId == target.id &&
                            !model.value.state.value.busy
                    }
                    assertEquals(
                        target.id,
                        runBlocking { app.database.dao().settings() }!!.calendarId,
                    )
                    compose
                        .onNode(hasScrollToIndexAction())
                        .performScrollToNode(hasTestTag("destination_calendar"))
                    compose.onNodeWithText("preferred-target", substring = true).assertIsDisplayed()
                    File(app.getExternalFilesDir(null), "qa-calendar-settings.png")
                        .outputStream()
                        .use {
                            compose
                                .onRoot()
                                .captureToImage()
                                .asAndroidBitmap()
                                .compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    compose.runOnIdle {
                        store.clear()
                        model.value = AppViewModel(app)
                        store.put("preferences-restarted", model.value)
                    }
                    compose.waitUntil(15000) {
                        model.value.state.value.loaded && !model.value.state.value.busy
                    }
                    assertEquals(target.id, model.value.state.value.settings.calendarId)
                    if (
                        compose
                            .onAllNodesWithTag("destination_calendar")
                            .fetchSemanticsNodes()
                            .isEmpty()
                    ) {
                        if (
                            compose
                                .onAllNodesWithTag("menu_calendar")
                                .fetchSemanticsNodes()
                                .isEmpty()
                        )
                            compose.onNodeWithText("Impostazioni").performClick()
                        compose.onNodeWithTag("menu_calendar").performScrollTo().performClick()
                    }
                    compose
                        .onNode(hasScrollToIndexAction())
                        .performScrollToNode(hasTestTag("destination_calendar"))
                    compose.onNodeWithText("preferred-target", substring = true).assertIsDisplayed()
                } finally {
                    compose.runOnIdle { store.clear() }
                    runBlocking {
                        if (oldSettings != null) app.database.dao().saveSettings(oldSettings)
                        else
                            app.database.openHelper.writableDatabase.execSQL(
                                "DELETE FROM settings WHERE id = 1"
                            )
                    }
                }
            }
        }
    }
}
