package it.turni.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import it.turni.app.imageprocessing.ImageProcessor
import it.turni.core.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ImageInput(
    val bitmap: Bitmap,
    val profile: EmployeeProfile,
    val referenceDate: LocalDate,
)

data class ExtractedSchedule(
    val tokens: List<OcrToken>,
    val table: ExtractionResult,
    val enhanced: Boolean,
    val elapsedMillis: Long,
)

interface ScheduleExtractor {
    suspend fun extract(image: ImageInput): ExtractedSchedule
}

class LocalScheduleExtractor(private val images: ImageProcessor) : ScheduleExtractor {
    private suspend fun recognize(bitmap: Bitmap): List<OcrToken> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            return result.textBlocks
                .flatMap { it.lines }
                .flatMap { it.elements }
                .mapNotNull { element ->
                    val rect = element.boundingBox ?: return@mapNotNull null
                    OcrToken(
                        element.text,
                        Box(
                            rect.left.toFloat() / bitmap.width,
                            rect.top.toFloat() / bitmap.height,
                            rect.right.toFloat() / bitmap.width,
                            rect.bottom.toFloat() / bitmap.height,
                        ),
                        element.confidence.takeIf { it >= 0f },
                    )
                }
        } finally {
            recognizer.close()
        }
    }

    override suspend fun extract(image: ImageInput): ExtractedSchedule =
        withContext(Dispatchers.Default) {
            val start = android.os.SystemClock.elapsedRealtime()
            val original = recognize(image.bitmap)
            val enhancedBitmap = images.contrast(image.bitmap)
            val enhanced =
                try {
                    recognize(enhancedBitmap)
                } finally {
                    enhancedBitmap.recycle()
                }
            val parser = TableParser()
            val a = parser.extract(original, image.profile, image.referenceDate)
            val b = parser.extract(enhanced, image.profile, image.referenceDate)
            fun quality(table: ExtractionResult) =
                table.cells.count { it.type == CellType.WORK_SHIFT || it.type == CellType.CODE } *
                    2.0 +
                    (table.selected?.score ?: 0.0) +
                    table.columns.count { it.date != null } * 0.1 -
                    table.issues.count { it != ExtractionIssue.DATE_CONFIRMATION } * 2
            val useEnhanced = quality(b) > quality(a)
            ExtractedSchedule(
                if (useEnhanced) enhanced else original,
                if (useEnhanced) b else a,
                useEnhanced,
                android.os.SystemClock.elapsedRealtime() - start,
            )
        }
}
