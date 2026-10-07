package it.turni.app.imageprocessing

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.security.MessageDigest
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Corner(val x: Float, val y: Float)

class ImageProcessor(private val context: Context) {
    suspend fun decode(uri: Uri): Bitmap =
        withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use {
                requireNotNull(it)
                BitmapFactory.decodeStream(it, null, bounds)
            }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "INVALID_IMAGE" }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            val bitmap =
                context.contentResolver.openInputStream(uri).use {
                    requireNotNull(it)
                    requireNotNull(BitmapFactory.decodeStream(it, null, options))
                }
            val orientation =
                context.contentResolver.openInputStream(uri).use {
                    requireNotNull(it)
                    ExifInterface(it)
                        .getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        )
                }
            val matrix =
                Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                        ExifInterface.ORIENTATION_TRANSPOSE -> {
                            postRotate(90f)
                            postScale(-1f, 1f)
                        }
                        ExifInterface.ORIENTATION_TRANSVERSE -> {
                            postRotate(270f)
                            postScale(-1f, 1f)
                        }
                    }
                }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }

    /**
     * Four corners in clockwise TL/TR/BR/BL order. Android's projective matrix rectifies the paper.
     */
    suspend fun rectify(bitmap: Bitmap, corners: List<Corner>): Bitmap =
        withContext(Dispatchers.Default) {
            require(corners.size == 4 && corners.all { it.x in 0f..1f && it.y in 0f..1f }) {
                "INVALID_CROP"
            }
            val source = corners.map { PointF(it.x * bitmap.width, it.y * bitmap.height) }
            val crosses =
                source.indices.map { i ->
                    val a = source[i]
                    val b = source[(i + 1) % 4]
                    val c = source[(i + 2) % 4]
                    (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
                }
            require(crosses.all { it > 1f }) { "INVALID_CROP" }
            fun distance(a: PointF, b: PointF) = hypot(a.x - b.x, a.y - b.y)
            val width =
                max(distance(source[0], source[1]), distance(source[3], source[2]))
                    .toInt()
                    .coerceIn(64, 4096)
            val height =
                max(distance(source[0], source[3]), distance(source[1], source[2]))
                    .toInt()
                    .coerceIn(64, 4096)
            val transform = Matrix()
            require(
                transform.setPolyToPoly(
                    source.flatMap { listOf(it.x, it.y) }.toFloatArray(),
                    0,
                    floatArrayOf(
                        0f,
                        0f,
                        width.toFloat(),
                        0f,
                        width.toFloat(),
                        height.toFloat(),
                        0f,
                        height.toFloat(),
                    ),
                    0,
                    4,
                )
            ) {
                "INVALID_CROP"
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { result ->
                Canvas(result).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(
                        bitmap,
                        transform,
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                    )
                }
            }
        }

    suspend fun contrast(bitmap: Bitmap): Bitmap =
        withContext(Dispatchers.Default) {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { result
                ->
                val gray = ColorMatrix().apply { setSaturation(0f) }
                gray.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.15f,
                            0f,
                            0f,
                            0f,
                            -15f,
                            0f,
                            1.15f,
                            0f,
                            0f,
                            -15f,
                            0f,
                            0f,
                            1.15f,
                            0f,
                            -15f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f,
                        )
                    )
                )
                Canvas(result)
                    .drawBitmap(
                        bitmap,
                        0f,
                        0f,
                        Paint().apply { colorFilter = ColorMatrixColorFilter(gray) },
                    )
            }
        }

    suspend fun fingerprint(bitmap: Bitmap): String =
        withContext(Dispatchers.Default) {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("${bitmap.width}|${bitmap.height}|".toByteArray())
            val row = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                row.forEach { pixel ->
                    digest.update((pixel shr 16).toByte())
                    digest.update((pixel shr 8).toByte())
                    digest.update(pixel.toByte())
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
}
