package it.turni.core

import java.time.LocalDate

/** Proposed OCR repairs only; manual input still goes through the strict ShiftParser. */
object OcrShiftRecovery {
    data class Proposal(val text: String, val ambiguous: Boolean)

    private const val MAX_INPUT_LENGTH = 64
    private val counter = Regex("^[0-9]{1,2}$")
    private val threeDigits = Regex("(?<![0-9])[0-9]{3}(?![0-9])")
    private val confusable = mapOf('O' to '0', 'I' to '1', 'L' to '1', '|' to '1')

    fun propose(raw: String, date: LocalDate): Proposal? {
        if (raw.length > MAX_INPUT_LENGTH || raw.contains('/')) return null
        if (ShiftParser.parse(raw, date).type != CellType.UNKNOWN) return null
        val normalized = TextNormalizer.normalize(raw)
        val parts = normalized.split(Regex("\\s+"))
        val leading = parts.takeWhile { counter.matches(it) }.size
        val trailing = parts.takeLastWhile { counter.matches(it) }.size
        val bodies = linkedSetOf<String>()
        for (left in 0..leading) for (right in 0..trailing) {
            if (left + right < parts.size)
                bodies += parts.subList(left, parts.size - right).joinToString(" ")
        }
        val candidates = linkedMapOf<String, String>()
        fun accept(text: String, allowCode: Boolean = false) {
            val cell = ShiftParser.parse(text, date)
            if (cell.type == CellType.WORK_SHIFT || (allowCode && cell.type == CellType.CODE))
                candidates.putIfAbsent(cell.signature(), text)
        }
        for (body in bodies) {
            accept(body, allowCode = true)
            // One character edit at most. Never truncate an arbitrary number to fit the grammar.
            body.forEachIndexed { index, character ->
                val digit = confusable[character] ?: return@forEachIndexed
                accept(body.replaceRange(index, index + 1, digit.toString()))
                accept(body.removeRange(index, index + 1))
            }
            for (run in threeDigits.findAll(body)) {
                for (offset in 1..2) {
                    if (run.value[offset] == run.value[offset - 1]) {
                        val index = run.range.first + offset
                        accept(body.removeRange(index, index + 1))
                    }
                }
            }
        }
        return when (candidates.size) {
            0 -> null
            1 -> Proposal(candidates.values.single(), ambiguous = false)
            else -> Proposal(candidates.values.joinToString(" / "), ambiguous = true)
        }
    }
}
