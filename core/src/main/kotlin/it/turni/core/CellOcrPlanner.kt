package it.turni.core

import kotlin.math.max

data class CellOcrPlan(val region: Box, val scale: Float)

/** Tight text crop with source-pixel margins; column dividers are only approximate text bounds. */
object CellOcrPlanner {
    fun plan(tokens: List<OcrToken>, cell: ShiftCell, width: Int, height: Int): CellOcrPlan? {
        require(width > 0 && height > 0)
        val source = cell.source ?: return null
        val inside = tokens.filter {
            it.centerX >= source.left && it.centerX < source.right && source.containsY(it.centerY)
        }
        fun readings(raw: String) =
            raw.split(" / ")
                .map { ShiftParser.parse(it, cell.date) }
                .filter {
                    it.type == CellType.WORK_SHIFT || it.type == CellType.CODE
                }
                .map { it.signature() }
        val signatures = readings(cell.raw)
        val matched = inside.filter { token -> readings(token.text).any { it in signatures } }
        val text = matched.ifEmpty { inside }
        val bounds = if (text.isEmpty()) source else Box.enclosing(text.map { it.box })
        val heights = text.map { it.box.height * height }.filter { it > 0f }.sorted()
        val glyphHeight =
            (heights.getOrNull(heights.size / 2) ?: (source.height * height)).coerceAtLeast(1f)
        val horizontalMargin = max(2f, glyphHeight * 0.75f) / width
        val verticalMargin = max(2f, glyphHeight * 0.25f) / height
        return CellOcrPlan(
            Box(
                (bounds.left - horizontalMargin).coerceIn(0f, 1f),
                (bounds.top - verticalMargin).coerceIn(0f, 1f),
                (bounds.right + horizontalMargin).coerceIn(0f, 1f),
                (bounds.bottom + verticalMargin).coerceIn(0f, 1f),
            ),
            (24f / glyphHeight).coerceIn(1f, 4f),
        )
    }
}
