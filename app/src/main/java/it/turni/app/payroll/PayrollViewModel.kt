package it.turni.app.payroll

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import it.turni.app.R
import it.turni.app.TurniApplication
import it.turni.core.*
import java.time.YearMonth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class PayrollPage {
    UPLOAD,
    REVIEW,
    RESULTS,
}

data class PayrollUiState(
    val page: PayrollPage = PayrollPage.UPLOAD,
    val reports: List<PayrollReportEntity> = emptyList(),
    val selected: PayrollReportEntity? = null,
    val report: PayrollReport? = null,
    val busy: Boolean = false,
    val progress: Int = 0,
    val error: String? = null,
    val preview: Bitmap? = null,
    val previewLoading: Boolean = false,
)

class PayrollViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val app = application as TurniApplication
    private val repository = PayrollRepository(app, app.database)
    private val mutable = MutableStateFlow(PayrollUiState())
    val state = mutable.asStateFlow()
    private var employee: String? = null
    private var month = YearMonth.now()
    private var observer: Job? = null
    private var work: Job? = null
    private var sourceJob: Job? = null

    fun start(employee: String, month: YearMonth) {
        if (this.employee == employee && observer?.isActive == true) return
        this.employee = employee
        this.month = month
        observer?.cancel()
        observer = viewModelScope.launch {
            var initial = true
            repository
                .reports(employee)
                .catch {
                    mutable.update { it.copy(error = app.getString(R.string.payroll_load_failed)) }
                }
                .collect { reports ->
                    mutable.update { it.copy(reports = reports) }
                    if (initial) {
                        initial = false
                        val selected =
                            reports.firstOrNull { it.id == saved.get<String>("selectedPayroll") }
                                ?: reports.firstOrNull { it.month == month.toString() }
                        if (selected != null) open(selected)
                    }
                }
        }
    }

    fun import(uri: Uri) = launchWork {
        mutable.update { it.copy(progress = 0) }
        val selected =
            repository.import(uri, requireNotNull(employee), month) { progress ->
                mutable.update { it.copy(progress = progress) }
            }
        load(selected)
    }

    fun open(entity: PayrollReportEntity) = launchWork { load(entity) }

    private suspend fun load(entity: PayrollReportEntity) {
        require(entity.employeeId == employee) { "PAYROLL_IDENTITY" }
        val report = withContext(Dispatchers.Default) { PayrollJson.decode(entity.payload) }
        require(report.month.toString() == entity.month) { "PAYROLL_CORRUPT" }
        saved["selectedPayroll"] = entity.id
        mutable.update {
            it.copy(
                selected = entity,
                report = report,
                page = if (report.identityConfirmed) PayrollPage.RESULTS else PayrollPage.REVIEW,
                error = null,
            )
        }
    }

    fun newImport() {
        if (!mutable.value.busy) mutable.update { it.copy(page = PayrollPage.UPLOAD, error = null) }
    }

    fun review() {
        if (!mutable.value.busy) mutable.update { it.copy(page = PayrollPage.REVIEW, error = null) }
    }

    fun dismissError() {
        mutable.update { it.copy(error = null) }
    }

    fun confirm(monthText: String, totalText: String, confirmed: Boolean) = launchWork {
        require(confirmed) { "PAYROLL_IDENTITY" }
        val period = runCatching { YearMonth.parse(monthText.trim()) }.getOrNull()
        require(period != null && period.year in 1900..2100) { "PAYROLL_PERIOD" }
        val printed = PayrollParser.duration(totalText, period.lengthOfMonth() * 1440)
        require(printed != null) { "PAYROLL_DURATION" }
        val current =
            requireNotNull(mutable.value.report)
                .copy(month = period, printedMinutes = printed, identityConfirmed = true)
        require(current.totalMatches) { "PAYROLL_TOTAL_MISMATCH" }
        save(current)
        mutable.update { it.copy(page = PayrollPage.RESULTS) }
    }

    fun saveDay(
        day: Int,
        minutes: String,
        punchText: String,
        daylightSavingReviewed: Boolean,
        continuedEntries: Set<Int>,
        onSaved: () -> Unit,
    ) = launchWork {
        val amount = PayrollParser.duration(minutes, 1440)
        require(amount != null) { "PAYROLL_DURATION" }
        val parsedPunches = PayrollParser.punches(punchText)
        require(parsedPunches != null) { "PAYROLL_STAMPS" }
        require(
            continuedEntries.all { minute ->
                day > 1 && parsedPunches.any { it.kind == PunchKind.ENTRY && it.minute == minute }
            }
        ) {
            "PAYROLL_STAMPS"
        }
        val punches = parsedPunches.map {
            if (it.kind == PunchKind.ENTRY && it.minute in continuedEntries)
                it.copy(previousWorkDay = true)
            else it
        }
        val report = requireNotNull(mutable.value.report)
        require(report.days.any { it.day == day })
        save(
            report.copy(
                days =
                    report.days.map {
                        if (it.day == day)
                            it.copy(
                                minutes = amount,
                                punches = punches,
                                reviewed = true,
                                daylightSavingReviewed = daylightSavingReviewed,
                            )
                        else it
                    }
            )
        )
        onSaved()
    }

    private suspend fun save(report: PayrollReport) {
        val entity = repository.save(requireNotNull(mutable.value.selected), report)
        mutable.update { it.copy(selected = entity, report = report) }
    }

    fun showSource(day: Int? = null, footer: Boolean = false) {
        val selected = mutable.value.selected ?: return
        val report = mutable.value.report ?: return
        sourceJob?.cancel()
        sourceJob = viewModelScope.launch {
            mutable.update { it.copy(previewLoading = true, preview = null, error = null) }
            try {
                val source =
                    if (day == null) {
                        if (footer) Box(0f, report.days.last().source.bottom, 1f, 1f)
                        else Box(0f, 0f, 1f, report.days.first().source.top)
                    } else {
                        val row = report.days.first { it.day == day }
                        val next = report.days.firstOrNull { it.day == day + 1 }
                        Box(
                            row.source.left,
                            row.source.top,
                            row.source.right,
                            next?.source?.bottom ?: row.source.bottom,
                        )
                    }
                val bitmap = repository.preview(selected, source)
                mutable.update { it.copy(preview = bitmap) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.update { it.copy(error = message(error)) }
            } finally {
                mutable.update { it.copy(previewLoading = false) }
            }
        }
    }

    fun dismissSource() {
        sourceJob?.cancel()
        mutable.update { it.copy(preview = null, previewLoading = false) }
    }

    fun delete() = launchWork {
        repository.delete(requireNotNull(mutable.value.selected))
        saved["selectedPayroll"] = null
        mutable.update {
            it.copy(selected = null, report = null, page = PayrollPage.UPLOAD, preview = null)
        }
    }

    private fun launchWork(block: suspend () -> Unit) {
        if (work?.isActive == true) return
        work = viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null, progress = -1) }
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.update { it.copy(error = message(error)) }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    private fun message(error: Exception) =
        app.getString(
            when (error.message) {
                "PAYROLL_GRID" -> R.string.payroll_unsupported
                "PAYROLL_PAGES" -> R.string.payroll_single_page
                "PAYROLL_TOO_LARGE" -> R.string.payroll_too_large
                "PAYROLL_INVALID_PDF" -> R.string.payroll_invalid_pdf
                "PAYROLL_IDENTITY" -> R.string.payroll_confirm_identity
                "PAYROLL_PERIOD" -> R.string.payroll_invalid_period
                "PAYROLL_DURATION" -> R.string.payroll_invalid_duration
                "PAYROLL_STAMPS" -> R.string.payroll_invalid_stamps
                "PAYROLL_TOTAL_MISMATCH" -> R.string.payroll_total_mismatch
                "PAYROLL_SOURCE_MISSING" -> R.string.payroll_source_missing
                "PAYROLL_CORRUPT",
                "PAYROLL_VERSION" -> R.string.payroll_corrupt
                else ->
                    if (error is SecurityException) R.string.payroll_pdf_access
                    else R.string.payroll_load_failed
            }
        )
}
