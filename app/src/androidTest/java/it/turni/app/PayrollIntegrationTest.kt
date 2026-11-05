package it.turni.app

import android.net.Uri
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.data.*
import it.turni.app.payroll.*
import it.turni.core.*
import java.io.File
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PayrollIntegrationTest {
    @get:Rule
    val migrations =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TurniDatabase::class.java)

    @Test
    fun syntheticPdfReadsAllDaysWithoutAddingPayCodes(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = PayrollFixture.pdf(context)
        try {
            val started = android.os.SystemClock.elapsedRealtime()
            val report = PayrollPdfReader().read(file, YearMonth.of(2026, 11))
            assertEquals(YearMonth.of(2026, 10), report.month)
            assertTrue(
                report.employeeName,
                PayrollParser.matchesName(report.employeeName, EmployeeProfile("Luca Rossi")),
            )
            assertEquals(6915, report.printedMinutes)
            assertEquals(PayrollFixture.minutes, report.days.map { it.minutes })
            assertTrue(report.totalMatches)
            assertTrue(report.days[23].daylightSaving)
            val timeline = PayrollComparator.timeline(report)
            assertEquals(
                report.month.atDay(4).atStartOfDay(),
                timeline.intervals[report.month.atDay(3)]!!.single().end,
            )
            assertEquals(
                183,
                java.time.Duration.between(
                        timeline.intervals[report.month.atDay(1)]!!.single().start,
                        timeline.intervals[report.month.atDay(1)]!!.single().end,
                    )
                    .toMinutes()
                    .toInt(),
            )
            android.util.Log.i(
                "PayrollQA",
                "synthetic elapsedMs=${android.os.SystemClock.elapsedRealtime()-started} days=${report.days.size} pending=${report.days.count { it.needsReview }} issues=${report.days.groupingBy { it.issues }.eachCount()}",
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun importIsDeduplicatedCorrectionsSurviveReopenAndChecksumIsVerified() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "payroll-repository-test.db"
        context.deleteDatabase(name)
        val fixture = PayrollFixture.pdf(context)
        var db = Room.databaseBuilder(context, TurniDatabase::class.java, name).build()
        var saved: PayrollReportEntity? = null
        try {
            var repository = PayrollRepository(context, db)
            val first =
                repository.import(Uri.fromFile(fixture), "payroll-test", YearMonth.of(2026, 10)) {}
            saved = first
            val parsed = PayrollJson.decode(first.payload)
            val changed =
                parsed.copy(
                    identityConfirmed = true,
                    days =
                        parsed.days.map {
                            if (it.day == 2) it.copy(minutes = 210, reviewed = true) else it
                        },
                )
            repository.save(first, changed)
            val duplicate =
                repository.import(Uri.fromFile(fixture), "payroll-test", YearMonth.of(2026, 10)) {}
            assertEquals(first.id, duplicate.id)
            assertEquals(210, PayrollJson.decode(duplicate.payload).days[1].minutes)
            db.close()
            db = Room.databaseBuilder(context, TurniDatabase::class.java, name).build()
            repository = PayrollRepository(context, db)
            val restored = repository.reports("payroll-test").first().single()
            assertEquals(changed, PayrollJson.decode(restored.payload))
            val preview = repository.preview(restored, parsed.days[1].source)
            assertTrue(preview.width > 0 && preview.height > 0)
            preview.recycle()
            val original = File(context.filesDir, "payroll/${restored.id}.pdf")
            original.appendBytes(byteArrayOf(1))
            val rejected = runCatching { repository.preview(restored, null) }
            assertEquals("PAYROLL_CORRUPT", rejected.exceptionOrNull()?.message)
            val repaired =
                repository.import(Uri.fromFile(fixture), "payroll-test", YearMonth.of(2026, 10)) {}
            assertEquals(restored.id, repaired.id)
            assertEquals(210, PayrollJson.decode(repaired.payload).days[1].minutes)
            repository.preview(repaired, null).recycle()
            repository.delete(restored)
            assertFalse(original.exists())
            assertTrue(repository.reports("payroll-test").first().isEmpty())
        } finally {
            saved?.let { File(context.filesDir, "payroll/${it.id}.pdf").delete() }
            db.close()
            context.deleteDatabase(name)
            fixture.delete()
        }
    }

    @Test
    fun invalidInputIsRejectedAndStagingFilesAreRemoved() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TurniDatabase::class.java).build()
        val invalid =
            File(context.cacheDir, "payroll-invalid.txt").apply { writeText("not a PDF file") }
        val huge = File(context.cacheDir, "payroll-huge.pdf")
        try {
            val repository = PayrollRepository(context, db)
            assertEquals(
                "PAYROLL_INVALID_PDF",
                runCatching {
                    repository.import(Uri.fromFile(invalid), "input-test", YearMonth.now()) {}
                }
                    .exceptionOrNull()
                    ?.message,
            )
            huge.outputStream().use { output ->
                output.write("%PDF-".toByteArray())
                val buffer = ByteArray(65536)
                repeat(321) { output.write(buffer) }
            }
            assertEquals(
                "PAYROLL_TOO_LARGE",
                runCatching {
                    repository.import(Uri.fromFile(huge), "input-test", YearMonth.now()) {}
                }
                    .exceptionOrNull()
                    ?.message,
            )
            assertTrue(repository.reports("input-test").first().isEmpty())
            assertTrue(
                File(context.filesDir, "payroll").listFiles().orEmpty().none {
                    it.extension == "tmp"
                }
            )
            assertTrue(runCatching { PayrollJson.decode("{\"version\":99}") }.isFailure)
        } finally {
            db.close()
            invalid.delete()
            huge.delete()
        }
    }

    @Test
    fun migrationFiveToSixPreservesExistingWorkAndAddsReportStorage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "payroll-migration-test.db"
        try {
            migrations.createDatabase(name, 5).apply {
                execSQL("INSERT INTO profiles VALUES ('existing', 'Test User', '')")
                execSQL(
                    "INSERT INTO extra_work VALUES ('extra','existing','2026-10-01','12:00','15:00',1)"
                )
                close()
            }
            migrations.runMigrationsAndValidate(name, 6, true, TurniDatabase.MIGRATION_5_6).close()
            val db = Room.databaseBuilder(context, TurniDatabase::class.java, name).build()
            try {
                assertEquals("Test User", db.dao().profile()!!.name)
                assertNotNull(db.dao().findExtra("existing", "2026-10-01", "12:00", "15:00"))
                assertTrue(db.dao().payrollReports("existing").first().isEmpty())
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
