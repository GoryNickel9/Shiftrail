package it.turni.core

import java.time.LocalDate
import kotlin.math.abs

/** Maps crop-local geometry back to the source, then merges overlapping OCR passes. */
object OcrGeometry {
    /** A local reread must not move the fitted row or alter other days' contents. */
    fun preservesOtherCells(
        before: ExtractionResult,
        after: ExtractionResult,
        date: LocalDate,
    ): Boolean {
        if (
            before.selected?.label != after.selected?.label || before.cells.size != after.cells.size
        )
            return false
        val previous = before.cells.associateBy { it.date }
        val next = after.cells.associateBy { it.date }
        if (date !in previous || previous.keys != next.keys || next.size != after.cells.size)
            return false
        return previous.all { (day, cell) ->
            val candidate = next.getValue(day)
            day == date ||
                (cell.signature() == candidate.signature() &&
                    (cell.type != CellType.UNKNOWN || cell.raw == candidate.raw))
        }
    }

    fun conflictingReadings(first: ShiftCell, second: ShiftCell): List<String> {
        val readable = setOf(CellType.WORK_SHIFT, CellType.CODE)
        val alternatives =
            (first.raw.split(" / ") + second.raw.split(" / "))
                .map { ShiftParser.parse(it, first.date) }
                .filter { it.type in readable }
                .distinctBy { it.signature() }
        return if (alternatives.size > 1) alternatives.map { it.raw } else emptyList()
    }

    fun map(box: Box, region: Box): Box =
        Box(
            region.left + box.left * (region.right - region.left),
            region.top + box.top * region.height,
            region.left + box.right * (region.right - region.left),
            region.top + box.bottom * region.height,
        )

    fun merge(tokens: List<OcrToken>): List<OcrToken> {
        val kept = mutableListOf<OcrToken>()
        val byText = mutableMapOf<String, MutableList<OcrToken>>()
        for (token in tokens.sortedByDescending { it.confidence ?: 0f }) {
            val sameText = byText.getOrPut(token.normalizedText) { mutableListOf() }
            if (
                sameText.none { other ->
                    val a = token.box
                    val b = other.box
                    val overlap =
                        (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f) *
                            (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
                    val area = minOf((a.right - a.left) * a.height, (b.right - b.left) * b.height)
                    area > 0 &&
                        overlap / area > 0.5f &&
                        abs(a.centerY - b.centerY) < maxOf(a.height, b.height) * 0.5f
                }
            ) {
                // Near-identical boxes can contain conflicting spellings from two passes.
                // Conflicting valid times remain visible and require an explicit correction.
                val conflict = kept.indexOfFirst { other ->
                    val a = token.box
                    val b = other.box
                    val intersection =
                        (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f) *
                            (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
                    val area = maxOf((a.right - a.left) * a.height, (b.right - b.left) * b.height)
                    area > 0 && intersection / area > 0.7f
                }
                if (conflict < 0) {
                    kept += token
                    sameText += token
                } else {
                    val other = kept[conflict]
                    val parts =
                        (other.text.split(" / ") + token.text.split(" / ")).map {
                            ShiftParser.parse(it, LocalDate.MIN)
                        }
                    if (parts.all { it.type == CellType.WORK_SHIFT }) {
                        val alternatives = parts.distinctBy { it.signature() }
                        if (alternatives.size > 1)
                            kept[conflict] =
                                other.copy(
                                    text = alternatives.joinToString(" / ") { it.raw },
                                    confidence = 0.5f,
                                )
                    }
                }
            }
        }
        return kept.sortedWith(compareBy({ it.centerY }, { it.centerX }))
    }
}
