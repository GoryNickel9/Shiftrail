package it.turni.core

import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

object TextNormalizer {
    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .uppercase(Locale.ROOT)
            .trim()
            .replace(Regex("\\s+"), " ")

    fun similarity(a: String, b: String): Double {
        val x =
            normalize(a)
                .replace(Regex("[^A-Z0-9 ]"), "")
                .split(' ')
                .filter { it.isNotEmpty() }
                .sorted()
                .joinToString(" ")
        val y =
            normalize(b)
                .replace(Regex("[^A-Z0-9 ]"), "")
                .split(' ')
                .filter { it.isNotEmpty() }
                .sorted()
                .joinToString(" ")
        if (x.isEmpty() || y.isEmpty()) return 0.0
        var previous = IntArray(y.length + 1) { it }
        for (i in x.indices) {
            val current = IntArray(y.length + 1)
            current[0] = i + 1
            for (j in y.indices) current[j + 1] =
                minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (x[i] == y[j]) 0 else 1)
            previous = current
        }
        return 1.0 - previous[y.length].toDouble() / maxOf(x.length, y.length)
    }
}

object ItalianDateParser {
    private val days =
        listOf("LUNEDI", "MARTEDI", "MERCOLEDI", "GIOVEDI", "VENERDI", "SABATO", "DOMENICA")
    private val months =
        mapOf(
            "GEN" to 1,
            "FEB" to 2,
            "MAR" to 3,
            "APR" to 4,
            "MAG" to 5,
            "GIU" to 6,
            "LUG" to 7,
            "AGO" to 8,
            "SET" to 9,
            "OTT" to 10,
            "OCT" to 10,
            "NOV" to 11,
            "DIC" to 12,
            "DEC" to 12,
        )
    private val numeric =
        Regex("(?<!\\d)(\\d{1,2})\\s*[/-]\\s*(\\d{1,2})(?:\\s*[/-]\\s*(\\d{4}))?(?!\\d)")
    private val named = Regex("(?<![A-Z0-9])(\\d{1,2})[ -]+([A-Z]{3,9})(?:[ -]+(\\d{4}))?(?!\\d)")

    data class ParsedDate(val date: LocalDate, val explicitYear: Boolean)

    fun weekday(text: String): Int? {
        val normalized = TextNormalizer.normalize(text)
        val words = normalized.split(Regex("[^A-Z]+"))
        return days.indices
            .firstOrNull { index ->
                words.any {
                    it == days[index] ||
                        it == days[index].take(3) ||
                        (it.length >= 5 && TextNormalizer.similarity(it, days[index]) >= 0.82)
                }
            }
            ?.plus(1)
    }

    fun date(text: String, year: Int): ParsedDate? {
        val s = TextNormalizer.normalize(text)
        val match = numeric.find(s) ?: named.find(s) ?: return null
        val month =
            match.groupValues[2].toIntOrNull()
                ?: months[match.groupValues[2].take(3)]
                ?: return null
        val explicit = match.groupValues[3].isNotBlank()
        return runCatching {
            ParsedDate(
                LocalDate.of(
                    if (explicit) match.groupValues[3].toInt() else year,
                    month,
                    match.groupValues[1].toInt(),
                ),
                explicit,
            )
        }
            .getOrNull()
    }
}

object ShiftParser {
    const val MAX_TIME_DIGITS = 8
    private val timeRange =
        Regex("^(\\d{1,2})(?:[:.](\\d{2}))?\\s*[-–—]\\s*(\\d{1,2})(?:[:.](\\d{2}))?$")
    const val REST_CODE = "X"

    /** 24:00 is the midnight alias used by rosters; all other hour-24 values are invalid. */
    fun clockTime(hour: Int, minute: Int): LocalTime =
        if (hour == 24 && minute == 0) LocalTime.MIDNIGHT else LocalTime.of(hour, minute)

    fun parse(
        raw: String,
        date: LocalDate,
        confidence: Double = 1.0,
        source: Box? = null,
    ): ShiftCell {
        val normalized = TextNormalizer.normalize(raw)
        if (normalized.isEmpty())
            return ShiftCell(date, raw, CellType.EMPTY, confidence = confidence, source = source)
        val match =
            if (
                normalized.count { it in '0'..'9' } <= MAX_TIME_DIGITS &&
                    normalized.none { it.isLetter() }
            )
                timeRange.matchEntire(normalized)
            else null
        if (match != null) {
            val start = runCatching {
                clockTime(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].ifBlank { "0" }.toInt(),
                )
            }
                .getOrNull()
            val end = runCatching {
                clockTime(
                    match.groupValues[3].toInt(),
                    match.groupValues[4].ifBlank { "0" }.toInt(),
                )
            }
                .getOrNull()
            if (start != null && end != null && start != end)
                return ShiftCell(
                    date,
                    raw,
                    CellType.WORK_SHIFT,
                    start,
                    end,
                    confidence = confidence,
                    source = source,
                )
        }
        if (normalized == REST_CODE)
            return ShiftCell(
                date,
                raw,
                CellType.CODE,
                code = normalized,
                confidence = confidence,
                source = source,
            )
        return ShiftCell(
            date,
            raw,
            CellType.UNKNOWN,
            confidence = minOf(confidence, 0.5),
            source = source,
        )
    }
}
