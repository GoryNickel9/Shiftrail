package it.turni.app.imageprocessing

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppBackground(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "app-background.jpg"))

    suspend fun load(): Bitmap? =
        withContext(Dispatchers.IO) {
            if (!file.baseFile.exists()) null
            else
                file.openRead().use {
                    requireNotNull(android.graphics.BitmapFactory.decodeStream(it)) {
                        "INVALID_IMAGE"
                    }
                }
        }

    suspend fun save(uri: Uri): Bitmap =
        withContext(Dispatchers.IO) {
            val decoded = ImageProcessor(context).decode(uri)
            val scale = minOf(1.0, 2048.0 / maxOf(decoded.width, decoded.height))
            val bitmap =
                if (scale < 1.0)
                    Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                else decoded
            if (bitmap !== decoded) decoded.recycle()
            val stream = file.startWrite()
            try {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) {
                    "BACKGROUND_WRITE_FAILED"
                }
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                bitmap.recycle()
                throw error
            }
            bitmap
        }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }
}
