package it.turni.app

import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.update.UpdateRepository
import it.turni.core.ReleaseUpdate
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UpdateTest {
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
