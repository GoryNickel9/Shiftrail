package it.turni.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        require(right >= left && bottom >= top)
    }

    val centerX
        get() = (left + right) / 2

    val centerY
        get() = (top + bottom) / 2

    val height
        get() = bottom - top

    fun containsY(y: Float) = y >= top && y <= bottom

    companion object {
        fun enclosing(boxes: List<Box>): Box {
            require(boxes.isNotEmpty())
            return Box(
                boxes.minOf { it.left },
                boxes.minOf { it.top },
                boxes.maxOf { it.right },
                boxes.maxOf { it.bottom },
            )
        }
    }
}

/** Coordinates are normalized to the analyzed bitmap, including after rectification. */
data class OcrToken(val text: String, val box: Box, val confidence: Float? = null) {
    val normalizedText
        get() = TextNormalizer.normalize(text)

    val centerX
        get() = box.centerX

    val centerY
        get() = box.centerY
}

data class EmployeeProfile(val name: String, val aliases: List<String> = emptyList()) {
    val searchNames
        get() = (listOf(name) + aliases).filter { it.isNotBlank() }
}

enum class CellType {
    WORK_SHIFT,
    CODE,
    EMPTY,
    UNKNOWN,
}

data class ShiftCell(
    val date: LocalDate,
    val raw: String,
    val type: CellType,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val code: String? = null,
    val confidence: Double = 1.0,
    val source: Box? = null,
    val reviewed: Boolean = false,
    val ocrOriginal: String? = null,
) {
    val endNextDay
        get() = start != null && end != null && end < start

    val needsReview
        get() =
            !confidence.isFinite() ||
                confidence < AUTO_ACCEPT_CONFIDENCE ||
                type == CellType.UNKNOWN

    /** Truncate decimal scores without binary multiplication artifacts or rounding up to 90. */
    val readingScore: Int
        get() =
            if (confidence.isFinite())
                BigDecimal.valueOf(confidence.coerceIn(0.0, 1.0)).movePointRight(2).toInt()
            else 0

    val endDate
        get() = if (endNextDay) date.plusDays(1) else date

    fun signature() = listOf(type, date, start, end, code).joinToString("|")

    companion object {
        /** Inclusive threshold for a valid reading to continue without an acknowledgement. */
        const val AUTO_ACCEPT_CONFIDENCE = 0.90
    }
}

data class RowCandidate(val label: String, val box: Box, val score: Double)

data class DayColumn(
    val weekday: Int,
    val center: Float,
    val left: Float,
    val right: Float,
    val date: LocalDate?,
    val explicitYear: Boolean,
)

enum class ExtractionIssue {
    EMPTY_OCR,
    NAME_NOT_FOUND,
    AMBIGUOUS_NAME,
    HEADER_NOT_FOUND,
    DATE_CONFIRMATION,
    INVALID_DATES,
}

data class ExtractionResult(
    val candidates: List<RowCandidate> = emptyList(),
    val selected: RowCandidate? = null,
    val rowBand: Box? = null,
    val columns: List<DayColumn> = emptyList(),
    val cells: List<ShiftCell> = emptyList(),
    val issues: Set<ExtractionIssue> = emptySet(),
)

enum class CalendarBehavior {
    IGNORE,
    ALL_DAY,
    CUSTOM,
}

data class CodeMapping(
    val code: String,
    val meaning: String,
    val behavior: CalendarBehavior,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
)
