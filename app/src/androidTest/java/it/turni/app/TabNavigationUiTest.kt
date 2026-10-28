package it.turni.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.data.SettingsEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TabNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var createdProfile: String? = null

    @After
    fun removeTemporaryProfile() {
        createdProfile?.let { id ->
            val app =
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                    as TurniApplication
            app.database.openHelper.writableDatabase.execSQL(
                "DELETE FROM profiles WHERE id = ?",
                arrayOf(id),
            )
        }
    }

    @Test
    fun reselectingSettingsReturnsFromEverySectionWithoutSavingDrafts() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("tab_settings").fetchSemanticsNodes().isNotEmpty()
        }
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val before = runBlocking { app.database.dao().settings() } ?: SettingsEntity()
        compose.onNodeWithTag("tab_settings").performClick()
        for (section in listOf("calendar", "background", "notifications", "updates")) {
            compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("menu_$section"))
            compose.onNodeWithTag("menu_$section").performClick()
            compose.onNodeWithTag("settings_back").assertDoesNotExist()
            compose
                .onNodeWithTag("settings_heading")
                .assertTextEquals(
                    when (section) {
                        "calendar" -> "Calendario"
                        "background" -> "Sfondo"
                        "notifications" -> "Notifiche"
                        else -> "Aggiornamenti"
                    }
                )
            compose.onNodeWithTag("tab_settings").performClick()
            compose.onNodeWithTag("settings_back").assertDoesNotExist()
            compose.onNodeWithTag("menu_calendar").assertExists()
        }
        assertEquals(before, runBlocking { app.database.dao().settings() } ?: SettingsEntity())
    }

    @Test
    fun reselectingOrarioReturnsToMonthAndSettingsAlwaysOpensItsMenu() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("tab_hours").fetchSemanticsNodes().isNotEmpty()
        }
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        if (runBlocking { app.database.dao().profile() } == null) {
            compose.onNodeWithTag("tab_settings").performClick()
            compose.onNodeWithText("Nome e cognome").performTextInput("Test navigazione")
            compose.onNodeWithText("Salva").performClick()
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("home_import_actions").fetchSemanticsNodes().isNotEmpty()
            }
            createdProfile = requireNotNull(runBlocking { app.database.dao().profile() }).id
        }
        compose.onNodeWithTag("tab_hours").performClick()
        compose.onNodeWithTag("choose_week").performClick()
        compose
            .onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("week_picker")))
            .performScrollToNode(hasTestTag("week_option_0"))
        compose.onNodeWithTag("week_option_0").performClick()
        compose.onNodeWithText("Mostra settimana").performClick()
        compose.onNodeWithTag("tab_hours").performClick()
        compose.onNodeWithTag("choose_week").assertTextContains("Settimana", substring = true)
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("menu_notifications"))
        compose.onNodeWithTag("menu_notifications").performClick()
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("menu_calendar").assertExists()
        compose.onNodeWithTag("settings_back").assertDoesNotExist()
    }

    @Test
    fun missingProfileSetupAlwaysShowsTheSettingsMenu() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val old = runBlocking { app.database.dao().profile() }
        try {
            if (old != null)
                app.database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM profiles WHERE id = ?",
                    arrayOf(old.id),
                )
            // Recreating alone retains the ViewModel and its profile loaded before fixture
            // deletion.
            compose.runOnIdle { compose.activity.viewModelStore.clear() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("tab_settings").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("tab_settings").performClick()
            compose
                .onNodeWithTag("settings_list")
                .performScrollToNode(hasTestTag("menu_notifications"))
            compose.onNodeWithTag("menu_notifications").performClick()
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithText("Configura il profilo").performScrollTo().performClick()
            compose.onNodeWithTag("settings_back").assertDoesNotExist()
            compose.onNodeWithTag("menu_calendar").assertExists()
            compose.onNodeWithText("Nome e cognome").performScrollTo().assertIsDisplayed()
            compose
                .onNodeWithTag("settings_list")
                .performScrollToNode(hasTestTag("menu_notifications"))
            compose.onNodeWithTag("menu_notifications").performClick()
            compose.onNodeWithTag("tab_hours").performClick()
            compose.onNodeWithText("Configura il profilo").performClick()
            compose.onNodeWithTag("settings_back").assertDoesNotExist()
            compose.onNodeWithTag("menu_calendar").assertExists()
        } finally {
            if (old != null) runBlocking { app.database.dao().saveProfile(old) }
        }
    }
}
