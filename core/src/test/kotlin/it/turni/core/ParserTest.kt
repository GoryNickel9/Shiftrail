package it.turni.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    private val monday = LocalDate.of(2026, 10, 5)

    @Test
    fun nativeOcrFloatScoresPreserveTheNinetyPercentBoundary() {
        for ((nativeScore, expected, review) in
            listOf(
                Triple(0.90f, 0.90, false),
                Triple(0.89f, 0.89, true),
                Triple(0.89999f, 0.89999, true),
            )) {
            val tokens =
                fixture().map { if (it.text == "19-01") it.copy(confidence = nativeScore) else it }
            val cell =
                TableParser()
                    .extract(tokens, EmployeeProfile("Luca Baldeddu"), monday)
                    .cells
                    .first()
            assertEquals(expected, cell.confidence, 0.0)
            assertEquals(review, cell.needsReview)
        }
    }

    @Test
    fun recoveredLettersAndRepeatedDigitsRequireSourceVerificationEvenAfterDateConfirmation() {
        for (raw in listOf("4 18:30-21I:30", "4 18:30-211:30")) {
            val tokens =
                fixture().filterNot { it.text == "19-01" || it.text == "6" } +
                    token(raw, 0.29f, 0.3f)
            for (confirmed in listOf<LocalDate?>(null, monday)) {
                val cell =
                    TableParser()
                        .extract(
                            tokens,
                            EmployeeProfile("Luca Baldeddu"),
                            monday,
                            confirmedMonday = confirmed,
                        )
                        .cells
                        .first()
                assertEquals(CellType.WORK_SHIFT, cell.type)
                assertEquals(LocalTime.of(18, 30), cell.start)
                assertEquals(LocalTime.of(21, 30), cell.end)
                assertEquals(raw, cell.ocrOriginal)
                assertFalse(cell.raw.any { it.isLetter() })
                assertTrue(cell.raw.count { it.isDigit() } <= ShiftParser.MAX_TIME_DIGITS)
                assertTrue(cell.needsReview)
                assertTrue(cell.confidence <= 0.65)
                val error =
                    assertThrows(IllegalArgumentException::class.java) {
                        EventFactory.create(
                            cell,
                            emptyList(),
                            EventOptions("Turno"),
                            ZoneId.of("Europe/Rome"),
                        )
                    }
                assertEquals("REVIEW_REQUIRED", error.message)
            }
        }
    }

    @Test
    fun strongNameDateAndSyntaxCannotRaiseWeakOrMissingOcrConfidence() {
        for (ocr in listOf<Float?>(0.12f, null, Float.NaN, -1f)) {
            val tokens = fixture().map { if (it.text == "19-01") it.copy(confidence = ocr) else it }
            val cell =
                TableParser()
                    .extract(tokens, EmployeeProfile("Luca Baldeddu"), monday)
                    .cells
                    .first()
            assertEquals(CellType.WORK_SHIFT, cell.type)
            assertTrue(cell.needsReview)
            assertEquals(if (ocr == 0.12f) 0.12 else 0.65, cell.confidence, 0.0001)
            val confirmed =
                TableParser()
                    .extract(
                        tokens,
                        EmployeeProfile("Luca Baldeddu"),
                        monday,
                        confirmedMonday = monday,
                    )
                    .cells
                    .first()
            assertEquals(cell.confidence, confirmed.confidence, 0.0)
        }
        val fragments =
            fixture().filterNot { it.text == "19-01" || it.text == "6" } +
                listOf(
                    token("19", 0.265f, 0.3f, 0.02f),
                    token("-", 0.285f, 0.3f, 0.008f).copy(confidence = 0.1f),
                    token("01", 0.305f, 0.3f, 0.02f),
                )
        assertEquals(
            0.1,
            TableParser()
                .extract(fragments, EmployeeProfile("Luca Baldeddu"), monday)
                .cells
                .first()
                .confidence,
            0.0001,
        )
        val missing = fragments.map { if (it.text == "-") it.copy(confidence = null) else it }
        assertEquals(
            0.65,
            TableParser()
                .extract(missing, EmployeeProfile("Luca Baldeddu"), monday)
                .cells
                .first()
                .confidence,
            0.0001,
        )
    }

    @Test
    fun omittedLeadingZeroInHourHasTheSameMeaningAndScore() {
        fun read(raw: String) =
            TableParser()
                .extract(
                    fixture().map { if (it.text == "19-01") it.copy(text = raw) else it },
                    EmployeeProfile("Luca Baldeddu"),
                    monday,
                )
                .cells
                .first()
        val short = read("22-1")
        val padded = read("22-01")
        assertEquals(padded.signature(), short.signature())
        assertEquals(padded.confidence, short.confidence, 0.0)
        assertNull(short.ocrOriginal)
    }

    @Test
    fun missingWeekdayCanUseExplicitDateButRequiresConfirmation() {
        val tokens = fixture().filterNot { it.text == "MERCOLEDI" }
        val result = TableParser().extract(tokens, EmployeeProfile("Luca Baldeddu"), monday)
        assertEquals(7, result.columns.size)
        assertEquals(7, result.cells.size)
        assertTrue(ExtractionIssue.DATE_CONFIRMATION in result.issues)
        assertEquals(LocalTime.of(23, 45), result.cells[2].start)
        val confirmed =
            TableParser()
                .extract(tokens, EmployeeProfile("Luca Baldeddu"), monday, confirmedMonday = monday)
        assertTrue(confirmed.issues.toString(), confirmed.issues.isEmpty())
    }

    @Test
    fun conflictingTimeReadingsRemainBlockedAfterDateConfirmation() {
        val tokens =
            fixture().map { if (it.text == "19-01") it.copy(text = "19-01 / 19-03") else it }
        val result =
            TableParser()
                .extract(tokens, EmployeeProfile("Luca Baldeddu"), monday, confirmedMonday = monday)
        assertEquals(CellType.UNKNOWN, result.cells.first().type)
        assertTrue(result.cells.first().needsReview)
    }

    @Test
    fun shiftsImmediatelyBelowHeaderAreNotParsedAsDates() {
        val compact =
            fixture().map { token ->
                val shift = if (token.centerY > 0.2f) -0.16f else 0f
                token.copy(
                    box =
                        token.box.copy(
                            top = token.box.top + shift,
                            bottom = token.box.bottom + shift,
                        )
                )
            } + token("22-01", 0.395f, 0.14f)
        val result = TableParser().extract(compact, EmployeeProfile("Luca Baldeddu"), monday)
        assertFalse(result.issues.toString(), ExtractionIssue.INVALID_DATES in result.issues)
        assertEquals(monday.plusDays(1), result.columns[1].date)
    }

    @Test
    fun timeFormatsAndOvernight() {
        for ((raw, start, end) in
            listOf(
                Triple("12-18", "12:00", "18:00"),
                Triple("19-01", "19:00", "01:00"),
                Triple("23:45-5:45", "23:45", "05:45"),
                Triple("23.45-05.45", "23:45", "05:45"),
                Triple("11:30-17:30", "11:30", "17:30"),
                Triple("6:30-12:30", "06:30", "12:30"),
            )) {
            val cell = ShiftParser.parse(raw, monday)
            assertEquals(CellType.WORK_SHIFT, cell.type)
            assertEquals(LocalTime.parse(start), cell.start)
            assertEquals(LocalTime.parse(end), cell.end)
            val duration =
                Duration.between(cell.date.atTime(cell.start), cell.endDate.atTime(cell.end))
            assertEquals(6, duration.toHours())
            assertEquals(end < start, cell.endNextDay)
        }
    }

    @Test
    fun codesAndInvalidTimes() {
        listOf("X", "x", " X ").forEach {
            assertEquals(CellType.CODE, ShiftParser.parse(it, monday).type)
            assertEquals("X", ShiftParser.parse(it, monday).code)
        }
        listOf(
                "FE",
                "PIR",
                "A.G.",
                "CORSO",
                "XX",
                "X X",
                "X.",
                "×",
                "6",
                "25-30",
                "12:60-18",
                "12-12",
                "12-18-20",
                "12-18 note",
                "24:01-06",
            )
            .forEach {
                assertEquals(it, CellType.UNKNOWN, ShiftParser.parse(it, monday).type)
            }
        assertEquals(CellType.EMPTY, ShiftParser.parse("", monday).type)
        assertEquals(monday.plusDays(1), ShiftParser.parse("19-00", monday).endDate)
        assertEquals(LocalTime.MIDNIGHT, ShiftParser.parse("24-01", monday).start)
        assertEquals(LocalTime.MIDNIGHT, ShiftParser.parse("19-24:00", monday).end)
    }

    @Test
    fun datesAndWeekdays() {
        listOf("5-ott-2026", "05/10", "05-10", "5 OCT", "05 OTT").forEach {
            assertEquals(it, monday, ItalianDateParser.date(it, 2026)?.date)
        }
        assertTrue(ItalianDateParser.date("5-ott-2026", 2025)!!.explicitYear)
        assertFalse(ItalianDateParser.date("05/10", 2026)!!.explicitYear)
        assertNull(ItalianDateParser.date("31/02/2026", 2026))
        assertEquals(1, ItalianDateParser.weekday("Lunedì"))
        assertEquals(3, ItalianDateParser.weekday("MERCOLEDI 07/10"))
        assertNull(ItalianDateParser.weekday("Luca"))
    }

    @Test
    fun fuzzyNameDoesNotUseSubstring() {
        assertEquals(1.0, TextNormalizer.similarity("BALDEDDU LUCA", "Luca Baldeddu"), 1e-9)
        assertTrue(TextNormalizer.similarity("BALDEDDU", "BALDED DU") > 0.8)
        assertTrue(TextNormalizer.similarity("Baldeddu Luca", "Baldeddu Marco") < 0.76)
        assertEquals(0.0, TextNormalizer.similarity("", "Luca"), 0.0)
    }

    private fun token(text: String, x: Float, y: Float, width: Float = 0.065f) =
        OcrToken(text, Box(x - width / 2, y - 0.009f, x + width / 2, y + 0.009f), 0.95f)

    private fun fixture(): List<OcrToken> {
        val days =
            listOf("LUNEDI", "MARTEDI", "MERCOLEDI", "GIOVEDI", "VENERDI", "SABATO", "DOMENICA")
        return buildList {
            days.forEachIndexed { i, day ->
                add(token(day, 0.29f + 0.105f * i, 0.08f))
                add(token("${5 + i}-ott-2026", 0.29f + 0.105f * i, 0.105f))
            }
            add(token("BALDEDDU LUCA", 0.1f, 0.3f, 0.17f))
            add(token("ROSSI MARCO", 0.1f, 0.35f, 0.17f))
            listOf("19-01", "X", "23:45-5:45", "FE", "12-18", "6:30-12:30", "A.G.")
                .forEachIndexed { i, s -> add(token(s, 0.29f + 0.105f * i, 0.3f)) }
            add(token("6", 0.33f, 0.3f, 0.008f))
            add(token("12-18", 0.29f, 0.35f))
        }
    }

    @Test
    fun rowAndColumnsDoNotReadOtherEmployeesOrCounters() {
        val result = TableParser().extract(fixture(), EmployeeProfile("Luca Baldeddu"), monday)
        assertTrue(result.issues.isEmpty())
        assertEquals(7, result.cells.size)
        assertEquals("19-01", result.cells[0].raw)
        assertTrue(result.cells[0].endNextDay)
        assertEquals(CellType.UNKNOWN, result.cells[3].type)
        assertEquals(CellType.UNKNOWN, result.cells[6].type)
        assertEquals("A.G.", result.cells[6].raw)
        assertTrue(result.cells.filter { it.type != CellType.UNKNOWN }.all { it.confidence >= 0.9 })
        assertEquals(monday.plusDays(6), result.cells[6].date)
    }

    @Test
    fun geometryIsScaleIndependentAndSplitTimeWorks() {
        val tokens = fixture().filterNot { it.text == "19-01" || it.text == "6" }.toMutableList()
        tokens += token("19", 0.265f, 0.3f, 0.02f)
        tokens += token("-", 0.285f, 0.3f, 0.008f)
        tokens += token("01", 0.305f, 0.3f, 0.02f)
        val result = TableParser().extract(tokens, EmployeeProfile("Luca Baldeddu"), monday)
        assertEquals(LocalTime.of(19, 0), result.cells.first().start)
    }

    @Test
    fun countersAroundFragmentedOrMergedTimesRequireReview() {
        val variants =
            listOf(
                listOf("4", "21:15", "-", "03:45"),
                listOf("21:15", "-", "03:45", "6"),
                listOf("4", "21:15", "-", "03:45", "6"),
                listOf("4 21:15-03:45"),
                listOf("21:15-03:45 6"),
                listOf("4 21:15-03:45 6"),
            )
        for (parts in variants) {
            val tokens =
                fixture().filterNot { it.text == "19-01" || it.text == "6" } +
                    parts.mapIndexed { i, part -> token(part, 0.25f + i * 0.015f, 0.3f, 0.012f) }
            val cell =
                TableParser()
                    .extract(tokens, EmployeeProfile("Luca Baldeddu"), monday)
                    .cells
                    .first()
            assertEquals(parts.toString(), CellType.WORK_SHIFT, cell.type)
            assertEquals(LocalTime.of(21, 15), cell.start)
            assertEquals(LocalTime.of(3, 45), cell.end)
            assertTrue(cell.endNextDay)
            assertTrue(parts.toString(), cell.needsReview)
            assertFalse(cell.reviewed)
            val confirmed =
                TableParser()
                    .extract(
                        tokens,
                        EmployeeProfile("Luca Baldeddu"),
                        monday,
                        confirmedMonday = monday,
                    )
                    .cells
                    .first()
            assertEquals(cell.signature(), confirmed.signature())
            assertTrue(confirmed.needsReview)
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    EventFactory.create(
                        confirmed,
                        emptyList(),
                        EventOptions("Turno"),
                        ZoneId.of("Europe/Rome"),
                    )
                }
            assertEquals("REVIEW_REQUIRED", error.message)
            assertNotNull(
                EventFactory.create(
                    confirmed.copy(reviewed = true),
                    emptyList(),
                    EventOptions("Turno"),
                    ZoneId.of("Europe/Rome"),
                )
            )
        }
    }

    @Test
    fun counterCleanupDoesNotGuessFromInvalidOrMultipleIntervals() {
        val variants =
            listOf(
                "4 25-03",
                "4 12-12",
                "4 21:15-03:45 note",
                "4 21:15-03:45 / 20-01",
                "21:15-03:45 6 20-01",
                "4",
                "100 21:15-03:45",
            )
        for (raw in variants) {
            val tokens =
                fixture().filterNot { it.text == "19-01" || it.text == "6" } +
                    token(raw, 0.29f, 0.3f)
            val cell =
                TableParser()
                    .extract(tokens, EmployeeProfile("Luca Baldeddu"), monday)
                    .cells
                    .first()
            assertEquals(raw, CellType.UNKNOWN, cell.type)
            assertEquals(raw, cell.raw)
            assertTrue(cell.needsReview)
        }
        assertEquals(CellType.UNKNOWN, ShiftParser.parse("4 21:15-03:45", monday).type)
    }

    @Test
    fun mergedCountersAroundCodesRequireReview() {
        for ((raw, code) in listOf("3 X" to "X", "X 6" to "X", "4 X 8" to "X")) {
            val tokens =
                fixture().filterNot { it.text == "19-01" || it.text == "6" } +
                    token(raw, 0.29f, 0.3f)
            val cell =
                TableParser()
                    .extract(tokens, EmployeeProfile("Luca Baldeddu"), monday)
                    .cells
                    .first()
            assertEquals(raw, CellType.CODE, cell.type)
            assertEquals(code, cell.code)
            assertTrue(cell.needsReview)
        }
    }

    @Test
    fun tiltedHeaderAndEmployeeRowStayTogether() {
        val tilted =
            fixture().map { token ->
                val shift = (if (token.centerY < 0.2f) 0.055f else 0.04f) * (token.centerX - 0.1f)
                token.copy(
                    box =
                        token.box.copy(
                            top = token.box.top + shift,
                            bottom = token.box.bottom + shift,
                        )
                )
            }
        val result = TableParser().extract(tilted, EmployeeProfile("Luca Baldeddu"), monday)
        assertTrue(result.issues.toString(), result.issues.isEmpty())
        assertEquals(7, result.columns.size)
        assertEquals(
            listOf("19-01", "X", "23:45-5:45", "FE", "12-18", "6:30-12:30", "A.G."),
            result.cells.map { it.raw },
        )
    }

    @Test
    fun ambiguousMatchMustBeSelected() {
        val tokens = fixture() + token("BALDEDDU LUCA", 0.1f, 0.4f, 0.17f)
        val result = TableParser().extract(tokens, EmployeeProfile("Luca Baldeddu"), monday)
        assertNull(result.selected)
        assertTrue(ExtractionIssue.AMBIGUOUS_NAME in result.issues)
        assertEquals(2, result.candidates.size)
    }

    @Test
    fun missingYearRequiresConfirmationAndExplicitDateMismatchBlocks() {
        val missing = fixture().map { it.copy(text = it.text.replace("-2026", "")) }
        assertTrue(
            ExtractionIssue.DATE_CONFIRMATION in
                TableParser().extract(missing, EmployeeProfile("Luca Baldeddu"), monday).issues
        )
        assertFalse(
            ExtractionIssue.DATE_CONFIRMATION in
                TableParser()
                    .extract(
                        missing,
                        EmployeeProfile("Luca Baldeddu"),
                        monday,
                        confirmedMonday = monday,
                    )
                    .issues
        )
        val wrong =
            fixture().map { if (it.text == "5-ott-2026") it.copy(text = "6-ott-2026") else it }
        assertTrue(
            ExtractionIssue.INVALID_DATES in
                TableParser().extract(wrong, EmployeeProfile("Luca Baldeddu"), monday).issues
        )
    }
}
