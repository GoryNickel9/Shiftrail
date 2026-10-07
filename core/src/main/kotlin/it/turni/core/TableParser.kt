package it.turni.core

import java.time.LocalDate
import kotlin.math.abs

/** Employee-row-first parser. No absolute image coordinates or whole-table transcription. */
class TableParser(
    private val matchThreshold: Double = 0.76,
    private val ambiguityMargin: Double = 0.08,
) {
    private val counter = Regex("^\\d{1,2}$")

    /** Recover only one complete reading after removing whitespace-separated edge counters. */
    private fun withoutEdgeCounters(raw: String, date: LocalDate): String? {
        if (ShiftParser.parse(raw, date).type != CellType.UNKNOWN) return null
        val parts = raw.trim().split(Regex("\\s+"))
        val leading = parts.takeWhile { counter.matches(it) }.size
        val trailing = parts.takeLastWhile { counter.matches(it) }.size
        val readings = mutableMapOf<String, String>()
        for (left in 0..leading) {
            for (right in 0..trailing) {
                if (left + right == 0 || left + right >= parts.size) continue
                val text = parts.subList(left, parts.size - right).joinToString(" ")
                val parsed = ShiftParser.parse(text, date)
                if (parsed.type == CellType.WORK_SHIFT || parsed.type == CellType.CODE)
                    readings.putIfAbsent(parsed.signature(), text)
            }
        }
        return readings.values.singleOrNull()
    }

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
        val slopedHeaders = headerTokens.flatMap { (_, a) ->
            headerTokens.mapNotNull { (_, b) ->
                val dx = b.centerX - a.centerX
                if (abs(dx) < 0.1f) return@mapNotNull null
                val slope = (b.centerY - a.centerY) / dx
                if (abs(slope) > 0.15f) return@mapNotNull null
                headerTokens
                    .map { it.second }
                    .filter { t ->
                        abs(t.centerY - a.centerY - slope * (t.centerX - a.centerX)) <
                            maxOf(a.box.height, t.box.height) * 0.7f
                    }
            }
        }
        val header =
            (headerRows + slopedHeaders)
                .maxByOrNull { row ->
                    row.mapNotNull { ItalianDateParser.weekday(it.text) }.distinct().size
                }
                .orEmpty()
        var headers =
            header
                .mapNotNull { token -> ItalianDateParser.weekday(token.text)?.let { it to token } }
                .distinctBy { it.first }
                .sortedBy { it.second.centerX }
        if (headers.size < 2)
            return ExtractionResult(issues = setOf(ExtractionIssue.HEADER_NOT_FOUND))
        val headerHeight = header.map { it.box.height }.sorted()[header.size / 2]
        val datedTokens = tokens.mapNotNull { token ->
            val date = ItalianDateParser.date(token.text, referenceDate.year)
            if (
                date?.explicitYear == true &&
                    token.centerY >= header.minOf { it.box.top } &&
                    token.centerY <= header.maxOf { it.box.bottom } + headerHeight * 2f
            )
                token to date.date
            else null
        }
        val dateAnchors =
            datedTokens
                .map { (_, date) -> date.minusDays((date.dayOfWeek.value - 1).toLong()) }
                .distinct()
        val inferred =
            if (datedTokens.map { it.second }.distinct().size >= 2 && dateAnchors.size == 1)
                datedTokens
                    .filter { (_, date) -> headers.none { it.first == date.dayOfWeek.value } }
                    .filter { (token, date) ->
                        val leftDay =
                            headers.lastOrNull { it.second.centerX < token.centerX }?.first ?: 0
                        val rightDay =
                            headers.firstOrNull { it.second.centerX > token.centerX }?.first ?: 8
                        date.dayOfWeek.value > leftDay && date.dayOfWeek.value < rightDay
                    }
                    .distinctBy { it.second.dayOfWeek.value }
                    .map { (token, date) ->
                        val first = headers.first().second
                        val last = headers.last().second
                        val slope =
                            if (last.centerX > first.centerX)
                                (last.centerY - first.centerY) / (last.centerX - first.centerX)
                            else 0f
                        val y = first.centerY + slope * (token.centerX - first.centerX)
                        date.dayOfWeek.value to
                            token.copy(
                                box =
                                    token.box.copy(
                                        top = y - headerHeight / 2,
                                        bottom = y + headerHeight / 2,
                                    )
                            )
                    }
            else emptyList()
        headers = (headers + inferred).sortedBy { it.second.centerX }
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
        if (inferred.isNotEmpty() && confirmedMonday == null)
            issues += ExtractionIssue.DATE_CONFIRMATION
        val chosen =
            selectedRow
                ?: candidates.firstOrNull()?.takeIf {
                    candidates.size == 1 || it.score - candidates[1].score >= ambiguityMargin
                }
        if (candidates.isEmpty() && selectedRow == null) issues += ExtractionIssue.NAME_NOT_FOUND
        else if (chosen == null) issues += ExtractionIssue.AMBIGUOUS_NAME
        val firstNameTop = nameRows.minOfOrNull { row -> row.minOf { it.box.top } } ?: 1f
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
                    it.centerY >= token.box.top &&
                    it.centerY < firstNameTop &&
                    abs(it.centerY - token.centerY) <= token.box.height * 3
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
        val nearRow = tokens.filter {
            it.centerX >= nameBoundary &&
                it.box.height < band.height * 1.5f &&
                abs(it.centerY - chosen.box.centerY) < band.height * 3
        }
        val slopes =
            listOf(0f) +
                nearRow.mapNotNull {
                    val dx = it.centerX - chosen.box.centerX
                    if (dx < 0.1f) null
                    else ((it.centerY - chosen.box.centerY) / dx).takeIf { s -> abs(s) <= 0.12f }
                }
        // Anchor the fitted row at the employee name; nearby rows must not shift this anchor.
        val rowSlope =
            slopes.minByOrNull { slope ->
                finalColumns
                    .mapNotNull { column ->
                        nearRow
                            .filter { it.centerX >= column.left && it.centerX < column.right }
                            .minOfOrNull {
                                val distance =
                                    abs(
                                        it.centerY -
                                            chosen.box.centerY -
                                            slope * (it.centerX - chosen.box.centerX)
                                    ) / band.height
                                distance * distance
                            }
                    }
                    .average()
                    .takeUnless { it.isNaN() } ?: Double.MAX_VALUE
            } ?: 0f
        val cells = finalColumns.mapNotNull { column ->
            val date = column.date ?: return@mapNotNull null
            val cellTokens = tokens.filter {
                band.containsY(it.centerY - rowSlope * (it.centerX - chosen.box.centerX)) &&
                    it.centerX >= column.left &&
                    it.centerX < column.right
            }
            // Standalone counters have no shift separator; exclude them before joining split OCR
            // elements.
            val filtered = cellTokens.filterNot {
                counter.matches(it.text.trim()) &&
                    cellTokens.any { t ->
                        ShiftParser.parse(t.text, date).type == CellType.WORK_SHIFT ||
                            ShiftParser.parse(t.text, date).type == CellType.CODE
                    }
            }
            val joined = filtered.sortedBy { it.box.left }.joinToString(" ") { it.text }
            val recovered = withoutEdgeCounters(joined, date)
            val raw = recovered ?: joined
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
                    // Recovery from fragmented/merged OCR requires an explicit source check.
                    .coerceIn(0.0, if (recovered != null) 0.65 else 1.0)
            ShiftParser.parse(
                raw,
                date,
                confidence,
                Box(
                    column.left,
                    (band.top +
                            minOf(
                                rowSlope * (column.left - chosen.box.centerX),
                                rowSlope * (column.right - chosen.box.centerX),
                            ))
                        .coerceIn(0f, 1f),
                    column.right,
                    (band.bottom +
                            maxOf(
                                rowSlope * (column.left - chosen.box.centerX),
                                rowSlope * (column.right - chosen.box.centerX),
                            ))
                        .coerceIn(0f, 1f),
                ),
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
