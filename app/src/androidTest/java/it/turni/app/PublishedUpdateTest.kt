package it.turni.app

import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.update.UpdateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in online verification after publishing, excluded from offline correctness claims. */
class PublishedUpdateTest {
    @Test
    fun publishedManifestAndApkPassTheRealAndroidUpdater() = runBlocking {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("verifyPublishedUpdate") == "true"
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val updater = UpdateRepository(context)
        val release = requireNotNull(updater.check())
        assertEquals(BuildConfig.VERSION_CODE, release.versionCode)
        assertEquals(BuildConfig.VERSION_NAME, release.versionName)
        assertFalse(
            release.isCompatibleUpdate(
                BuildConfig.VERSION_CODE.toLong(),
                android.os.Build.VERSION.SDK_INT,
            )
        )
        val apk = updater.download(release)
        assertTrue(apk.isFile)
        assertTrue(apk.length() > 0)
        val intent = updater.installIntent()
        if (context.packageManager.canRequestPackageInstalls()) {
            assertEquals("content", intent.data?.scheme)
            assertEquals("${context.packageName}.updates", intent.data?.authority)
            assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        } else {
            assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        }
    }
}
