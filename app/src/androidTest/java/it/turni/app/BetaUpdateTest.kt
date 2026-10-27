package it.turni.app

import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.update.UpdateRepository
import it.turni.core.ReleaseUpdate
import org.junit.Assert.*
import org.junit.Test

class BetaUpdateTest {
    @Test
    fun betaIsOptInPersistentOptionalAndCanBeLeftOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("updater", 0)
        val previous = preferences.all.toMap()
        preferences.edit().clear().commit()
        try {
            val beta =
                ReleaseUpdate(BuildConfig.VERSION_CODE + 2, "99.0.0-beta.1", "a".repeat(64), 26)
            val stable = beta.copy(versionCode = beta.versionCode - 1, versionName = "98.0.0")
            val updater = UpdateRepository(context)
            assertFalse(updater.betaEnabled())
            updater.rememberUpdate(beta)
            assertNull(updater.requiredUpdate())
            assertNull(updater.betaUpdate())
            updater.setBetaEnabled(true)
            updater.rememberUpdate(beta)
            assertEquals(beta, UpdateRepository(context).betaUpdate())
            assertNull(updater.requiredUpdate())
            updater.rememberUpdate(stable)
            assertEquals(stable, updater.requiredUpdate())
            updater.setBetaEnabled(false)
            assertNull(UpdateRepository(context).betaUpdate())
            assertEquals(stable, updater.requiredUpdate())
            assertFalse(UpdateRepository(context).betaEnabled())
            updater.setBetaEnabled(true)
            assertNull(updater.betaUpdate())
        } finally {
            val editor = preferences.edit().clear()
            for ((key, value) in previous) when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                is Int -> editor.putInt(key, value)
            }
            editor.commit()
        }
    }

    @Test
    fun onlyPublishedBetaTagsCanSupplyDownloadPaths() {
        val releases =
            """[
            {"draft":false,"prerelease":false,"tag_name":"v3.0.0","published_at":"2026-10-27T12:00:00Z"},
            {"draft":true,"prerelease":true,"tag_name":"v3.0.0-beta.3","published_at":"2026-10-27T12:00:00Z"},
            {"draft":false,"prerelease":true,"tag_name":"../payload","published_at":"2026-10-27T12:00:00Z"},
            {"draft":false,"prerelease":true,"tag_name":"v3.0.0-beta.2","published_at":"2026-10-26T12:00:00Z"},
            {"draft":false,"prerelease":true,"tag_name":"v3.0.0-beta.1","published_at":"2026-10-25T12:00:00Z"},
            {"draft":false,"prerelease":true,"tag_name":"v4.0.0-beta.1","published_at":null}
        ]"""
        assertEquals(listOf("v3.0.0-beta.2", "v3.0.0-beta.1"), UpdateRepository.betaTags(releases))
        assertEquals(
            "v0.5.0-beta.1",
            UpdateRepository.parseManifest(
                    """{"schemaVersion":1,"versionCode":16,"versionName":"0.5.0-beta.1","sha256":"${"a".repeat(64)}","minSdk":26}"""
                )
                .tag,
        )
    }
}
