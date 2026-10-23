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
