package it.turni.core

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CellOcrPlannerTest {
    private val date = LocalDate.of(2026, 11, 16)
    private val column = Box(0.3f, 0.4f, 0.5f, 0.45f)

    @Test
    fun textCrossingApproximateDividerIsNotClippedAndCountersAreExcluded() {
        val time = OcrToken("21:15-03:45", Box(0.29f, 0.415f, 0.46f, 0.435f))
        val counter = OcrToken("6", Box(0.48f, 0.415f, 0.49f, 0.435f))
        val neighbor = time.copy(box = Box(0.29f, 0.47f, 0.46f, 0.49f))
        val plan =
            CellOcrPlanner.plan(
                listOf(time, counter, neighbor),
                ShiftParser.parse(time.text, date, source = column),
                1000,
                1000,
            )!!
        assertTrue(plan.region.left < time.box.left)
        assertTrue(plan.region.right > time.box.right)
        assertTrue(plan.region.right < counter.box.left)
        assertTrue(plan.region.bottom < neighbor.box.top)
        assertEquals(1.2f, plan.scale, 0.0001f)
    }

    @Test
    fun fragmentsAndDisagreementsKeepCompleteTextGeometry() {
        val fragments =
            listOf(
                OcrToken("21", Box(0.32f, 0.415f, 0.35f, 0.435f)),
                OcrToken("-", Box(0.36f, 0.415f, 0.37f, 0.435f)),
                OcrToken("03", Box(0.38f, 0.415f, 0.41f, 0.435f)),
            )
        val plan =
            CellOcrPlanner.plan(
                fragments,
                ShiftParser.parse("21 - 03", date, source = column),
                1000,
                1000,
            )!!
        assertTrue(plan.region.left < fragments.first().box.left)
        assertTrue(plan.region.right > fragments.last().box.right)
        val alternative = OcrToken("21-03 / 21-01", Box(0.31f, 0.415f, 0.43f, 0.435f))
        val ambiguous =
            CellOcrPlanner.plan(
                listOf(alternative),
                ShiftParser.parse(alternative.text, date, source = column),
                1000,
                1000,
            )!!
        assertTrue(ambiguous.region.left < alternative.box.left)
        assertTrue(ambiguous.region.right > alternative.box.right)
    }

    @Test
    fun marginsAndScalingRemainBoundedAtImageEdges() {
        val token = OcrToken("18-00", Box(0f, 0f, 0.1f, 0.005f))
        val cell = ShiftParser.parse(token.text, date, source = Box(0f, 0f, 0.2f, 0.02f))
        val small = CellOcrPlanner.plan(listOf(token), cell, 100, 100)!!
        assertEquals(0f, small.region.left, 0f)
        assertEquals(0f, small.region.top, 0f)
        assertEquals(4f, small.scale, 0f)
        assertEquals(1f, CellOcrPlanner.plan(listOf(token), cell, 10000, 10000)!!.scale, 0f)
        val fallback = CellOcrPlanner.plan(emptyList(), cell, 100, 100)!!
        assertTrue(fallback.region.right >= cell.source!!.right)
        assertNull(CellOcrPlanner.plan(emptyList(), cell.copy(source = null), 100, 100))
    }
}
