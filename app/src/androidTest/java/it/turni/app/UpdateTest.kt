package it.turni.app

import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.update.UpdateRepository
import it.turni.core.ReleaseUpdate
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UpdateTest {
    @Test
    fun requiredUpdateSurvivesRestartAndClearsAfterInstallation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("updater", 0)
        val previous = preferences.getString("required_release", null)
        preferences.edit().remove("required_release").commit()
        try {
            val update = ReleaseUpdate(BuildConfig.VERSION_CODE + 2, "99.0.0", "a".repeat(64), 26)
            UpdateRepository(context).rememberUpdate(update)
            assertEquals(update, UpdateRepository(context).requiredUpdate())
            UpdateRepository(context)
                .rememberUpdate(update.copy(versionCode = BuildConfig.VERSION_CODE + 1))
            assertEquals(update, UpdateRepository(context).requiredUpdate())
            assertNull(UpdateRepository(context).requiredUpdate(update.versionCode.toLong()))
            assertNull(UpdateRepository(context).requiredUpdate())
            UpdateRepository(context)
                .rememberUpdate(update.copy(minSdk = android.os.Build.VERSION.SDK_INT + 1))
            assertNull(UpdateRepository(context).requiredUpdate())
        } finally {
            preferences.edit().putString("required_release", previous).commit()
        }
    }

    @Test
    fun manifestRequiresKnownSchemaAndValidMetadata() {
        val valid =
            """{"schemaVersion":1,"versionCode":10,"versionName":"0.3.0","sha256":"${"a".repeat(64)}","minSdk":26}"""
        assertEquals("Shiftrail-0.3.0.apk", UpdateRepository.parseManifest(valid).apkName)
        assertThrows(IllegalArgumentException::class.java) {
            UpdateRepository.parseManifest(
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateRepository.parseManifest(valid.replace("0.3.0", "../payload"))
        }
    }

    @Test
    fun apkIdentityVersionAndSignatureAreCheckedBeforeInstallation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val updater = UpdateRepository(context)
        val release =
            ReleaseUpdate(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, "a".repeat(64), 26)
        updater.validateApk(File(context.applicationInfo.sourceDir), release)
        assertThrows(IllegalArgumentException::class.java) {
            updater.validateApk(
                File(context.applicationInfo.sourceDir),
                release.copy(versionCode = release.versionCode + 1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            updater.validateApk(File(instrumentation.context.applicationInfo.sourceDir), release)
        }
        val invalid = File(context.cacheDir, "invalid-update.apk")
        try {
            invalid.writeText("not an APK")
            assertThrows(IllegalArgumentException::class.java) {
                updater.validateApk(invalid, release)
            }
        } finally {
            invalid.delete()
        }
    }
}
