package it.turni.app

import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.payroll.PayrollPdfReader
import it.turni.core.PayrollComparator
import java.io.File
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class PrivatePayrollPdfTest {
    @Test
    fun suppliedPrivatePdfIsReadLocally(): Unit = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("payrollPdfPath")
        assumeTrue("Optional private regression requires payrollPdfPath", path != null)
        val source = File(requireNotNull(path))
        val started = android.os.SystemClock.elapsedRealtime()
        val report = PayrollPdfReader().read(source, YearMonth.of(2026, 11))
        assertEquals(YearMonth.of(2026, 10), report.month)
        assertEquals(6915, report.printedMinutes)
        assertEquals(PayrollFixture.minutes, report.days.map { it.minutes })
        assertTrue(report.totalMatches)
        assertTrue(report.days[23].daylightSaving)
        assertTrue(PayrollComparator.timeline(report).intervals.isNotEmpty())
        android.util.Log.i(
            "PayrollQA",
            "private elapsedMs=${android.os.SystemClock.elapsedRealtime()-started} days=${report.days.size} pending=${report.days.count { it.needsReview }} issues=${report.days.groupingBy { it.issues }.eachCount()}",
        )
    }
}
