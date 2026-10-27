package it.turni.core

import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdateTest {
    private val release = ReleaseUpdate(10, "0.3.0", "a".repeat(64), 26)

    @Test
    fun newerCompatibleVersionsOnly() {
        assertTrue(release.isCompatibleUpdate(9, 26))
        assertFalse(release.isCompatibleUpdate(10, 35))
        assertFalse(release.isCompatibleUpdate(11, 35))
        assertFalse(release.isCompatibleUpdate(9, 25))
        assertEquals("v0.3.0", release.tag)
        assertEquals("Shiftrail-0.3.0.apk", release.apkName)
    }

    @Test
    fun betaMetadataHasSafePathsAndStableVersionsCanFollowBetaBuilds() {
        val beta = release.copy(versionCode = 11, versionName = "0.4.0-beta.1")
        assertTrue(beta.isBeta)
        assertEquals("v0.4.0-beta.1", beta.tag)
        assertEquals("Shiftrail-0.4.0-beta.1.apk", beta.apkName)
        assertFalse(release.isBeta)
        assertTrue(release.copy(versionCode = 12).isCompatibleUpdate(beta.versionCode.toLong(), 35))
        for (name in
            listOf(
                "0.4.0-beta.0",
                "0.4.0-beta.01",
                "0.4.0-beta.1/other",
                "0.4.0-alpha.1",
                "0.4.0-beta",
            )) {
            assertThrows(IllegalArgumentException::class.java) { release.copy(versionName = name) }
        }
    }

    @Test
    fun invalidMetadataCannotConstructDownloadPaths() {
        for (name in listOf("../0.3.0", "0.3.0/other", "0.3.0?x=1", "v0.3.0", "", "0.3")) {
            assertThrows(IllegalArgumentException::class.java) { release.copy(versionName = name) }
        }
        assertThrows(IllegalArgumentException::class.java) { release.copy(sha256 = "a".repeat(63)) }
        assertThrows(IllegalArgumentException::class.java) { release.copy(sha256 = "z".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { release.copy(versionCode = 0) }
        assertThrows(IllegalArgumentException::class.java) { release.copy(minSdk = 25) }
    }
}
