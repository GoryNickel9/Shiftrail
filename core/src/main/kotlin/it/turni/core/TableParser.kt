package it.turni.core

import java.time.LocalDate
import kotlin.math.abs

/** Employee-row-first parser. No absolute image coordinates or whole-table transcription. */
class TableParser(
    private val matchThreshold: Double = 0.76,
    private val ambiguityMargin: Double = 0.08,
) {
    private fun rows(tokens: List<OcrToken>): List<List<OcrToken>> {
        val rows = mutableListOf<MutableList<OcrToken>>()
        for (token in tokens.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            if (
                row != null &&
                    abs(row.map { it.centerY }.average() - token.centerY) <
                        maxOf(row.maxOf { it.box.height }, token.box.height) * 0.7
            )
                row.add(token)
            else rows.add(mutableListOf(token))
        }
        return rows.map { it.sortedBy(OcrToken::centerX) }
    }

    fun extract(
        tokens: List<OcrToken>,
        profile: EmployeeProfile,
        referenceDate: LocalDate,
        selectedRow: RowCandidate? = null,
        confirmedMonday: LocalDate? = null,
    ): ExtractionResult {
        if (tokens.isEmpty()) return ExtractionResult(issues = setOf(ExtractionIssue.EMPTY_OCR))
        val headerTokens = tokens.mapNotNull { token ->
            ItalianDateParser.weekday(token.text)?.let { it to token }
        }
        // Pick a coherent horizontal header, rather than matching weekday text in arbitrary cells.
        val headerRows = rows(headerTokens.map { it.second })
        val header =
            headerRows
                .maxByOrNull { row ->
                    row.mapNotNull { ItalianDateParser.weekday(it.text) }.distinct().size
                }
                .orEmpty()
        val headers =
            header
                .mapNotNull { token -> ItalianDateParser.weekday(token.text)?.let { it to token } }
                .distinctBy { it.first }
                .sortedBy { it.second.centerX }
        if (headers.size < 2)
            return ExtractionResult(issues = setOf(ExtractionIssue.HEADER_NOT_FOUND))
        val spacing =
            headers.zipWithNext { a, b -> b.second.centerX - a.second.centerX }.average().toFloat()
        val nameBoundary = headers.first().second.centerX - spacing * 0.5f
        val nameRows =
            rows(
                tokens.filter {
                    it.centerX < nameBoundary && it.centerY > header.maxOf { t -> t.box.bottom }
                }
            )
        val candidates =
            nameRows
                .map { row ->
                    val text = row.joinToString(" ") { it.text }
                    RowCandidate(
                        text,
                        Box.enclosing(row.map { it.box }),
                        profile.searchNames.maxOfOrNull { TextNormalizer.similarity(text, it) }
                            ?: 0.0,
                    )
                }
                .filter { it.score >= matchThreshold }
                .sortedByDescending { it.score }
        val issues = mutableSetOf<ExtractionIssue>()
        val chosen =
            selectedRow
                ?: candidates.firstOrNull()?.takeIf {
                    candidates.size == 1 || it.score - candidates[1].score >= ambiguityMargin
                }
        if (candidates.isEmpty() && selectedRow == null) issues += ExtractionIssue.NAME_NOT_FOUND
        else if (chosen == null) issues += ExtractionIssue.AMBIGUOUS_NAME
        val columns = headers.mapIndexed { index, (weekday, token) ->
            val left =
                if (index == 0) nameBoundary
                else (headers[index - 1].second.centerX + token.centerX) / 2
            val right =
                if (index == headers.lastIndex) minOf(1f, token.centerX + spacing / 2)
                else (token.centerX + headers[index + 1].second.centerX) / 2
            val dateTokens = tokens.filter {
                it.centerX >= left &&
                    it.centerX < right &&
                    abs(it.centerY - token.centerY) <= maxOf(token.box.height * 4, 0.06f)
            }
            val dateTexts =
                dateTokens.map { it.text } +
                    rows(dateTokens).map { row -> row.joinToString(" ") { it.text } }
            val dates =
                dateTexts
                    .mapNotNull { ItalianDateParser.date(it, referenceDate.year) }
                    .distinctBy { it.date }
            val parsed = dates.firstOrNull()
            if (dates.map { it.date }.distinct().size > 1) issues += ExtractionIssue.INVALID_DATES
            DayColumn(
                weekday,
                token.centerX,
                left,
                right,
                parsed?.date,
                parsed?.explicitYear == true,
            )
        }
        val anchors =
            columns
                .mapNotNull { column -> column.date?.minusDays((column.weekday - 1).toLong()) }
                .distinct()
        if (
            anchors.size > 1 ||
                columns.any { it.date != null && it.date.dayOfWeek.value != it.weekday }
        )
            issues += ExtractionIssue.INVALID_DATES
        val monday = confirmedMonday ?: anchors.singleOrNull()
        if (
            confirmedMonday == null &&
                (columns.size != 7 ||
                    columns.any { it.date == null || !it.explicitYear } ||
                    monday == null)
        )
            issues += ExtractionIssue.DATE_CONFIRMATION
        val finalColumns = columns.map { column ->
            column.copy(
                date =
                    if (confirmedMonday != null)
                        confirmedMonday.plusDays((column.weekday - 1).toLong())
                    else column.date ?: monday?.plusDays((column.weekday - 1).toLong())
            )
        }
        if (chosen == null)
            return ExtractionResult(candidates, columns = finalColumns, issues = issues)
        val neighborCenters =
            nameRows
                .map { Box.enclosing(it.map { t -> t.box }).centerY }
                .filter { abs(it - chosen.box.centerY) > chosen.box.height * 0.4f }
                .sorted()
        val above = neighborCenters.lastOrNull { it < chosen.box.centerY }
        val below = neighborCenters.firstOrNull { it > chosen.box.centerY }
        val top =
            above?.let { (it + chosen.box.centerY) / 2 }
                ?: chosen.box.top - chosen.box.height * 0.45f
        val bottom =
            below?.let { (it + chosen.box.centerY) / 2 }
                ?: chosen.box.bottom + chosen.box.height * 0.45f
        val band = Box(0f, maxOf(0f, top), 1f, minOf(1f, bottom))
        val cells = finalColumns.mapNotNull { column ->
            val date = column.date ?: return@mapNotNull null
            val cellTokens = tokens.filter {
                band.containsY(it.centerY) && it.centerX >= column.left && it.centerX < column.right
            }
            // Standalone counters have no shift separator; exclude them before joining split OCR
            // elements.
            val filtered = cellTokens.filterNot {
                Regex("^\\d{1,2}$").matches(it.text.trim()) &&
                    cellTokens.any { t ->
                        ShiftParser.parse(t.text, date).type == CellType.WORK_SHIFT ||
                            ShiftParser.parse(t.text, date).type == CellType.CODE
                    }
            }
            val raw = filtered.sortedBy { it.box.left }.joinToString(" ") { it.text }
            val ocr =
                filtered
                    .mapNotNull { it.confidence?.toDouble() }
                    .average()
                    .takeUnless { it.isNaN() } ?: 0.75
            val association =
                if (filtered.any { it.box.left < column.left || it.box.right > column.right }) 0.65
                else 1.0
            val dateQuality = if (column.explicitYear || confirmedMonday != null) 1.0 else 0.6
            val syntax = if (ShiftParser.parse(raw, date).type == CellType.UNKNOWN) 0.0 else 1.0
            val confidence =
                (0.25 * ocr +
                        0.25 * chosen.score +
                        0.2 * association +
                        0.15 * dateQuality +
                        0.15 * syntax)
                    .coerceIn(0.0, 1.0)
            ShiftParser.parse(
                raw,
                date,
                confidence,
                Box(column.left, band.top, column.right, band.bottom),
            )
        }
        return ExtractionResult(candidates, chosen, band, finalColumns, cells, issues)
    }

    fun selectableRows(tokens: List<OcrToken>, columns: List<DayColumn>): List<RowCandidate> =
        rows(tokens.filter { it.centerX < (columns.firstOrNull()?.left ?: 0.22f) }).map {
            RowCandidate(
                it.joinToString(" ") { t -> t.text },
                Box.enclosing(it.map { t -> t.box }),
                0.5,
            )
        }
}
