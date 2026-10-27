package it.turni.app

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.imageprocessing.AppBackground
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AppBackgroundTest {
    @Test
    fun personalImageIsBoundedPersistsAndSurvivesFailedReplacement() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "background-test").apply { mkdirs() }
        val context =
            object : ContextWrapper(base) {
                override fun getFilesDir(): File = directory
            }
        val image = File(directory, "source.png")
        val invalid = File(directory, "invalid.txt").apply { writeText("not an image") }
        val bitmap =
            Bitmap.createBitmap(3000, 1000, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
            }
        try {
            image.outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val backgrounds = AppBackground(context)
            assertNull(backgrounds.load())
            val selected = backgrounds.save(Uri.fromFile(image))
            assertEquals(2048, selected.width)
            assertEquals(682, selected.height)
            val reloaded = requireNotNull(AppBackground(context).load())
            assertEquals(selected.width, reloaded.width)
            val pixel = reloaded.getPixel(0, 0)
            assertTrue(Color.blue(pixel) >= 250 && Color.red(pixel) <= 5 && Color.green(pixel) <= 5)
            try {
                backgrounds.save(Uri.fromFile(invalid))
                fail("Invalid image accepted")
            } catch (_: IllegalArgumentException) {}
            assertEquals(pixel, requireNotNull(backgrounds.load()).getPixel(0, 0))
            backgrounds.clear()
            assertNull(AppBackground(context).load())
        } finally {
            bitmap.recycle()
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }
}
