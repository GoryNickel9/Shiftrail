package it.turni.app.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK;

    fun isDark(systemDark: Boolean): Boolean =
        when (this) {
            SYSTEM -> systemDark
            LIGHT -> false
            DARK -> true
        }
}

/** Device-only appearance preferences do not change the calendar or the Room schema. */
class AppAppearance(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun theme(): AppTheme =
        AppTheme.entries.firstOrNull { it.name == preferences.getString("theme", null) }
            ?: AppTheme.SYSTEM

    suspend fun save(theme: AppTheme) =
        withContext(Dispatchers.IO) {
            check(preferences.edit().putString("theme", theme.name).commit()) {
                "APPEARANCE_WRITE_FAILED"
            }
        }
}
