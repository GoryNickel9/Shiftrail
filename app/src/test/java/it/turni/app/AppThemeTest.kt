package it.turni.app

import it.turni.app.ui.AppTheme
import org.junit.Assert.*
import org.junit.Test

class AppThemeTest {
    @Test
    fun systemFollowsBothDeviceModes() {
        assertFalse(AppTheme.SYSTEM.isDark(false))
        assertTrue(AppTheme.SYSTEM.isDark(true))
    }

    @Test
    fun explicitModesOverrideTheDevice() {
        for (systemDark in listOf(false, true)) {
            assertFalse(AppTheme.LIGHT.isDark(systemDark))
            assertTrue(AppTheme.DARK.isDark(systemDark))
        }
    }
}
