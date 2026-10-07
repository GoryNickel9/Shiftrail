package it.turni.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.imageprocessing.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageProcessorTest {
    private val images = ImageProcessor(InstrumentationRegistry.getInstrumentation().targetContext)
    private val full = listOf(Corner(0f, 0f), Corner(1f, 0f), Corner(1f, 1f), Corner(0f, 1f))

    @Test
    fun identityRectificationAndFingerprint() = runBlocking {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(64, 64, Color.BLACK)
        val rectified = images.rectify(bitmap, full)
        assertEquals(128, rectified.width)
        assertEquals(Color.BLACK, rectified.getPixel(64, 64))
        assertEquals(images.fingerprint(bitmap), images.fingerprint(rectified))
        rectified.setPixel(0, 0, Color.BLACK)
        assertNotEquals(images.fingerprint(bitmap), images.fingerprint(rectified))
        bitmap.recycle()
        rectified.recycle()
    }

    @Test
    fun crossedCornersAreRejected() = runBlocking {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        try {
            val failure = runCatching {
                images.rectify(bitmap, listOf(full[0], full[2], full[1], full[3]))
            }
                .exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
        } finally {
            bitmap.recycle()
        }
    }
}
