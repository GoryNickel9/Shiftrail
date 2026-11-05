package it.turni.app.payroll

import android.content.Context
import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import it.turni.app.data.TurniDatabase
import it.turni.core.*
import java.io.File
import java.security.MessageDigest
import java.time.YearMonth
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Entity(
    tableName = "payroll_reports",
    indices =
        [
            Index(value = ["employeeId", "month"]),
            Index(value = ["employeeId", "sourceHash"], unique = true),
        ],
)
data class PayrollReportEntity(
    @PrimaryKey val id: String,
    val employeeId: String,
    val month: String,
    val sourceHash: String,
    val createdAt: Long,
    val updatedAt: Long,
    val payload: String,
)

object PayrollJson {
    fun encode(report: PayrollReport): String =
        JSONObject()
            .apply {
                put("version", 1)
                put("month", report.month.toString())
                put("employeeName", report.employeeName ?: JSONObject.NULL)
                put("printedMinutes", report.printedMinutes ?: JSONObject.NULL)
                put("identityConfirmed", report.identityConfirmed)
                put(
                    "days",
                    JSONArray().apply {
                        report.days.forEach { day ->
                            put(
                                JSONObject().apply {
                                    put("day", day.day)
                                    put("minutes", day.minutes ?: JSONObject.NULL)
                                    put("raw", day.raw)
                                    put("reviewed", day.reviewed)
                                    put("dst", day.daylightSaving)
                                    put("dstReviewed", day.daylightSavingReviewed)
                                    put(
                                        "source",
                                        JSONArray(
                                            listOf(
                                                day.source.left,
                                                day.source.top,
                                                day.source.right,
                                                day.source.bottom,
                                            )
                                        ),
                                    )
                                    put("issues", JSONArray(day.issues.map { it.name }))
                                    put(
                                        "punches",
                                        JSONArray().apply {
                                            day.punches.forEach { punch ->
                                                put(
                                                    JSONObject()
                                                        .put("kind", punch.kind.name)
                                                        .put("minute", punch.minute)
                                                        .put(
                                                            "previousWorkDay",
                                                            punch.previousWorkDay,
                                                        )
                                                )
                                            }
                                        },
                                    )
                                }
                            )
                        }
                    },
                )
            }
            .toString()

    fun decode(text: String): PayrollReport {
        require(text.length <= 262144) { "PAYROLL_CORRUPT" }
        val value = JSONObject(text)
        require(value.getInt("version") == 1) { "PAYROLL_VERSION" }
        val days = value.getJSONArray("days")
        require(days.length() in 28..31) { "PAYROLL_CORRUPT" }
        return PayrollReport(
            YearMonth.parse(value.getString("month")),
            if (value.isNull("employeeName")) null else value.getString("employeeName"),
            if (value.isNull("printedMinutes")) null else value.getInt("printedMinutes"),
            (0 until days.length()).map { index ->
                val day = days.getJSONObject(index)
                val box = day.getJSONArray("source")
                val punches = day.getJSONArray("punches")
                require(punches.length() <= 10 && box.length() == 4)
                val issues = day.getJSONArray("issues")
                require(issues.length() <= PayrollReadIssue.entries.size)
                PayrollDay(
                    day.getInt("day"),
                    if (day.isNull("minutes")) null else day.getInt("minutes"),
                    (0 until punches.length()).map { p ->
                        punches.getJSONObject(p).let {
                            PayrollPunch(
                                PunchKind.valueOf(it.getString("kind")),
                                it.getInt("minute"),
                                it.optBoolean("previousWorkDay", false),
                            )
                        }
                    },
                    day.getString("raw"),
                    Box(
                        box.getDouble(0).toFloat(),
                        box.getDouble(1).toFloat(),
                        box.getDouble(2).toFloat(),
                        box.getDouble(3).toFloat(),
                    ),
                    (0 until issues.length())
                        .map { PayrollReadIssue.valueOf(issues.getString(it)) }
                        .toSet(),
                    day.getBoolean("reviewed"),
                    day.getBoolean("dst"),
                    day.getBoolean("dstReviewed"),
                )
            },
            value.getBoolean("identityConfirmed"),
        )
    }
}

class PayrollRepository(private val context: Context, private val database: TurniDatabase) {
    private val dao = database.dao()
    private val directory =
        File(context.filesDir, "payroll").apply { check(isDirectory || mkdirs()) }
    private val reader = PayrollPdfReader()

    fun reports(employee: String) = dao.payrollReports(employee)

    suspend fun import(
        uri: Uri,
        employee: String,
        month: YearMonth,
        progress: (Int) -> Unit,
    ): PayrollReportEntity {
        val staged = File(directory, "import-${UUID.randomUUID()}.tmp")
        try {
            val hash =
                withContext(Dispatchers.IO) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        staged.outputStream().use { output ->
                            val buffer = ByteArray(32768)
                            var size = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                size += count
                                require(size <= MAX_BYTES) { "PAYROLL_TOO_LARGE" }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                            require(size >= 5) { "PAYROLL_INVALID_PDF" }
                            output.fd.sync()
                        }
                    }
                    java.io.DataInputStream(staged.inputStream()).use { input ->
                        val signature = ByteArray(5)
                        input.readFully(signature)
                        require(signature.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))) {
                            "PAYROLL_INVALID_PDF"
                        }
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
            dao.payrollByHash(employee, hash)?.let { existing ->
                PayrollJson.decode(existing.payload)
                withContext(Dispatchers.IO) {
                    val original = file(existing)
                    if (
                        !original.isFile ||
                            original.length() > MAX_BYTES ||
                            checksum(original) != hash
                    )
                        check(staged.renameTo(original)) { "PAYROLL_IO" }
                }
                return existing
            }
            val report = reader.read(staged, month, progress)
            val now = System.currentTimeMillis()
            val entity =
                PayrollReportEntity(
                    UUID.randomUUID().toString(),
                    employee,
                    report.month.toString(),
                    hash,
                    now,
                    now,
                    PayrollJson.encode(report),
                )
            withContext(Dispatchers.IO) { check(staged.renameTo(file(entity))) { "PAYROLL_IO" } }
            try {
                dao.savePayroll(entity)
            } catch (error: Throwable) {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    file(entity).delete()
                }
                throw error
            }
            return entity
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                if (staged.exists()) staged.delete()
            }
        }
    }

    suspend fun save(entity: PayrollReportEntity, report: PayrollReport): PayrollReportEntity {
        val updated =
            entity.copy(
                month = report.month.toString(),
                payload = PayrollJson.encode(report),
                updatedAt = System.currentTimeMillis(),
            )
        dao.savePayroll(updated)
        return updated
    }

    suspend fun preview(entity: PayrollReportEntity, source: Box?): android.graphics.Bitmap {
        withContext(Dispatchers.IO) {
            val sourceFile = file(entity)
            require(sourceFile.isFile && sourceFile.length() <= MAX_BYTES) {
                "PAYROLL_SOURCE_MISSING"
            }
            val hash = checksum(sourceFile)
            require(hash == entity.sourceHash) { "PAYROLL_CORRUPT" }
        }
        return reader.preview(file(entity), source)
    }

    suspend fun delete(entity: PayrollReportEntity) =
        withContext(Dispatchers.IO) {
            val source = file(entity)
            check(!source.exists() || source.delete()) { "PAYROLL_IO" }
            dao.removePayroll(entity.id, entity.employeeId)
        }

    private fun file(entity: PayrollReportEntity): File {
        require(
            runCatching { UUID.fromString(entity.id).toString() == entity.id }.getOrDefault(false)
        ) {
            "PAYROLL_CORRUPT"
        }
        return File(directory, "${entity.id}.pdf")
    }

    private fun checksum(file: File): String =
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(32768)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

    companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
    }
}
