package it.turni.core

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReviewPolicyTest {
    private val date = LocalDate.of(2026, 11, 16)

    @Test
    fun displayedScoreUsesDecimalDigitsAndNeverRoundsAnUncertainReadingUpToNinety() {
        assertEquals(58, ShiftParser.parse("22-1", date, 0.58).readingScore)
        val below = ShiftParser.parse("22-1", date, 0.899999)
        assertEquals(89, below.readingScore)
        assertTrue(below.needsReview)
        val boundary = ShiftParser.parse("22-1", date, 0.90)
        assertEquals(90, boundary.readingScore)
        assertFalse(boundary.needsReview)
    }

    @Test
    fun ninetyIsInclusiveAndLowerScoresRequireAcknowledgement() {
        for (score in listOf(0.90, 0.91, 1.0)) {
            assertFalse(ShiftParser.parse("22-1", date, score).needsReview)
        }
        for (score in listOf(0.899999, 0.79, 0.65, 0.0)) {
            assertTrue(ShiftParser.parse("22-1", date, score).needsReview)
        }
    }

    @Test
    fun unknownAndNonFiniteReadingsCannotBeAutoAccepted() {
        assertTrue(ShiftParser.parse("22-01 / 2-01", date, 1.0).needsReview)
        for (score in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertTrue(ShiftParser.parse("22-1", date, score).needsReview)
        }
    }

    @Test
    fun calendarEnforcesTheSameThresholdOutsideTheUi() {
        val zone = ZoneId.of("Europe/Rome")
        val options = EventOptions("Turno di lavoro")
        assertNotNull(
            EventFactory.create(ShiftParser.parse("22-1", date, 0.90), emptyList(), options, zone)
        )
        val uncertain = ShiftParser.parse("22-1", date, 0.899999)
        val failure = runCatching {
            EventFactory.create(uncertain, emptyList(), options, zone)
        }
            .exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals("REVIEW_REQUIRED", failure?.message)
        assertNotNull(
            EventFactory.create(uncertain.copy(reviewed = true), emptyList(), options, zone)
        )
    }
}
