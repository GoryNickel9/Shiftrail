package it.turni.core

import java.time.YearMonth

/** INAZ RP7000 has ten alternating E/U columns followed by payroll codes. */
object PayrollParser {
    private val duration = Regex("(\\d{1,3}):([0-5]\\d)")
    private val total = Regex("(\\d{1,3}):([0-5]\\d)\\s*T[O0]T\\s*LAV\\b", RegexOption.IGNORE_CASE)
    private val months =
        listOf(
            "gennaio",
            "febbraio",
            "marzo",
            "aprile",
            "maggio",
            "giugno",
            "luglio",
            "agosto",
            "settembre",
            "ottobre",
            "novembre",
            "dicembre",
        )

    fun duration(text: String, maximum: Int = 44640): Int? {
        val match = duration.matchEntire(text.trim()) ?: return null
        return (match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()).takeIf {
            it <= maximum
        }
    }

    fun printedTotal(tokens: List<OcrToken>): Int? {
        val lines = mutableListOf<MutableList<OcrToken>>()
        for (token in tokens.sortedBy { it.centerY }) {
            val line = lines.lastOrNull()
            if (line != null && kotlin.math.abs(token.centerY - line.first().centerY) < .006f)
                line += token
            else lines += mutableListOf(token)
        }
        return lines
            .flatMap { line ->
                total.findAll(line.sortedBy { it.centerX }.joinToString(" ") { it.text }).toList()
            }
            .singleOrNull()
            ?.let { duration("${it.groupValues[1]}:${it.groupValues[2]}") }
    }

    fun month(tokens: List<OcrToken>): YearMonth? {
        // The fixed month vocabulary permits the OCR substitution 0 -> o; identity is still
        // confirmed.
        fun monthWord(value: String) = value.lowercase().trim().replace('0', 'o')
        val monthToken =
            tokens.firstOrNull { token -> months.any { monthWord(token.text) == it } }
                ?: return null
        val year =
            tokens
                .filter { it.text.matches(Regex("(?:19|20|21)\\d{2}")) }
                .minByOrNull { kotlin.math.abs(it.centerY - monthToken.centerY) }
                ?.text
                ?.toIntOrNull() ?: return null
        return if (year in 1900..2100)
            YearMonth.of(year, months.indexOf(monthWord(monthToken.text)) + 1)
        else null
    }

    fun employeeName(tokens: List<OcrToken>): String? {
        val label = tokens.firstOrNull { it.text.equals("COGNOME", true) } ?: return null
        val candidates = tokens.filter {
            it.centerY > label.box.bottom &&
                it.centerY < label.box.bottom + .065f &&
                it.centerX < .48f &&
                it.text.any(Char::isLetter) &&
                it.text.uppercase() !in setOf("NOME", "COGNOME", "COD.", "DIP.", "MATRICOLA")
        }
        val first = candidates.minByOrNull { it.centerY } ?: return null
        return candidates
            .filter {
                kotlin.math.abs(it.centerY - first.centerY) < maxOf(.004f, first.box.height * .6f)
            }
            .sortedBy { it.centerX }
            .joinToString(" ") { it.text }
            .take(200)
            .ifBlank { null }
    }

    fun matchesName(name: String?, profile: EmployeeProfile): Boolean {
        fun words(value: String) =
            TextNormalizer.normalize(value)
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotBlank() }
                .sorted()
        return name != null && profile.searchNames.any { words(it) == words(name) }
    }

    fun row(day: Int, tokens: List<OcrToken>, source: Box, timeColumns: List<Float>): PayrollDay {
        require(timeColumns.size == 11 && timeColumns.zipWithNext().all { (a, b) -> b > a })
        val ordered = tokens.sortedBy { it.centerX }
        val issues = mutableSetOf<PayrollReadIssue>()
        val dayText =
            ordered.filter { it.centerX < timeColumns.first() }.joinToString("") { it.text }.trim()
        if (dayText.toIntOrNull() != day) issues += PayrollReadIssue.DAY
        val amounts = ordered.filter { it.centerX >= timeColumns.last() }
        val codes = amounts.joinToString(" ") { it.text }
        val matches = total.findAll(codes).toList()
        val minutes =
            if (codes.isBlank()) 0
            else
                matches.singleOrNull()?.let {
                    duration("${it.groupValues[1]}:${it.groupValues[2]}", 1440)
                }
        if (minutes == null) issues += PayrollReadIssue.TOTAL
        val stamps = mutableListOf<Pair<Float, PayrollPunch>>()
        for (token in ordered.filter { it.centerX in timeColumns.first()..timeColumns.last() }) {
            val found = duration.findAll(token.text).toList()
            if (found.isEmpty() && token.text.any(Char::isLetterOrDigit))
                issues += PayrollReadIssue.TIME
            for (match in found) {
                val minute = duration(match.value, 1440)
                val x =
                    token.box.left +
                        token.box.let { it.right - it.left } *
                            (match.range.first + match.value.length / 2f) /
                            token.text.length.coerceAtLeast(1)
                val column =
                    timeColumns.zipWithNext().indexOfFirst { (left, right) ->
                        x >= left && x < right
                    }
                if (minute == null || column < 0) issues += PayrollReadIssue.TIME
                else
                    stamps +=
                        x to
                            PayrollPunch(
                                if (column % 2 == 0) PunchKind.ENTRY else PunchKind.EXIT,
                                minute,
                            )
            }
        }
        if (stamps.size > 10) issues += PayrollReadIssue.TIME
        var offset = 0
        val totalMatch = matches.singleOrNull()
        val totalTokens = amounts.filter { token ->
            val start = offset
            offset += token.text.length + 1
            totalMatch != null &&
                start <= totalMatch.range.last &&
                offset - 2 >= totalMatch.range.first
        }
        // Unused allowance labels do not lower confidence in the actual clock/TOTLAV fields.
        val critical = ordered.filter { it.centerX < timeColumns.last() } + totalTokens
        if (
            critical.any {
                it.confidence == null || !it.confidence.isFinite() || it.confidence < .85f
            }
        )
            issues += PayrollReadIssue.CONFIDENCE
        return PayrollDay(
            day,
            minutes,
            stamps.sortedBy { it.first }.take(10).map { it.second },
            ordered.joinToString(" ") { it.text }.take(4096),
            source,
            issues,
            daylightSaving =
                Regex("\\b502\\b|ORA\\s*SOLARE", RegexOption.IGNORE_CASE).containsMatchIn(codes),
        )
    }

    /** Manual edits retain the E/U order of a PDF row, including exits after midnight. */
    fun punches(text: String): List<PayrollPunch>? {
        if (text.isBlank()) return emptyList()
        val parts = text.split(';').map { it.trim() }
        if (parts.size > 10) return null
        return parts.map { part ->
            val match =
                Regex("([EU])\\s*(\\d{1,2}:[0-5]\\d)", RegexOption.IGNORE_CASE).matchEntire(part)
                    ?: return null
            val minute = duration(match.groupValues[2], 1440) ?: return null
            PayrollPunch(
                if (match.groupValues[1].equals("E", true)) PunchKind.ENTRY else PunchKind.EXIT,
                minute,
            )
        }
    }
}
