package it.turni.core

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class OcrShiftRecoveryTest {
    private val date = LocalDate.of(2026, 11, 16)

    @Test
    fun oneStrayLetterOrRepeatedDigitProducesAUniqueCompleteProposal() {
        for (raw in listOf("4 18:30-21I:30", "4 18:30-211:30", "17:15-20L:45 6", "O7:15-13:15")) {
            val proposal = OcrShiftRecovery.propose(raw, date)!!
            assertFalse(raw, proposal.ambiguous)
            val cell = ShiftParser.parse(proposal.text, date)
            assertEquals(raw, CellType.WORK_SHIFT, cell.type)
            assertFalse(proposal.text.any { it.isLetter() })
            assertTrue(proposal.text.count { it.isDigit() } <= ShiftParser.MAX_TIME_DIGITS)
        }
        val repaired =
            ShiftParser.parse(OcrShiftRecovery.propose("4 18:30-21I:30", date)!!.text, date)
        assertEquals(LocalTime.of(18, 30), repaired.start)
        assertEquals(LocalTime.of(21, 30), repaired.end)
        assertEquals("18:30-21:30", OcrShiftRecovery.propose("4 18:30-211:30", date)!!.text)
    }

    @Test
    fun competingValidInterpretationsRemainUnknown() {
        val proposal = OcrShiftRecovery.propose("I8:30-23:30", date)!!
        assertTrue(proposal.ambiguous)
        val alternatives =
            proposal.text.split(" / ").map { ShiftParser.parse(it, date).start }.toSet()
        assertEquals(setOf(LocalTime.of(18, 30), LocalTime.of(8, 30)), alternatives)
        val cell = ShiftParser.parse(proposal.text, date)
        assertEquals(CellType.UNKNOWN, cell.type)
        assertTrue(cell.needsReview)
    }

    @Test
    fun validShortHoursAndCodesAreUnchangedAndManualParserStaysStrict() {
        for (raw in listOf("22-1", "22-01", "22:11-01:11", "X", "FE", "CORSO", "A.G.")) assertNull(
            raw,
            OcrShiftRecovery.propose(raw, date),
        )
        assertEquals(
            ShiftParser.parse("22-01", date).signature(),
            ShiftParser.parse("22-1", date).signature(),
        )
        for (raw in listOf("18:30-21I:30", "18:30-211:30", "I8:30-23:30")) assertEquals(
            raw,
            CellType.UNKNOWN,
            ShiftParser.parse(raw, date).type,
        )
    }

    @Test
    fun digitLimitNeverAuthorizesArbitraryTruncationOrSeveralEdits() {
        for (raw in
            listOf(
                "23:30-02:306",
                "12:34-05:678",
                "18:30-213:30",
                "1111-08",
                "I8:30-2O:30",
                "18:30-21:30 NOTE",
                "18-23 / 19-23",
                "25-03",
                "44-09",
                "x".repeat(10000),
            )) assertNull(raw.take(40), OcrShiftRecovery.propose(raw, date))
        assertEquals(CellType.UNKNOWN, ShiftParser.parse("12:34-05:678", date).type)
    }
}
