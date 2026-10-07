package it.turni.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.turni.app.R
import it.turni.app.TurniApplication
import it.turni.app.calendar.*
import it.turni.app.data.*
import it.turni.app.imageprocessing.*
import it.turni.app.ocr.*
import it.turni.core.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AppState(
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val profile: ProfileEntity? = null,
    val settings: SettingsEntity = SettingsEntity(),
    val upcoming: List<ShiftEntity> = emptyList(),
    val image: Bitmap? = null,
    val analyzed: Bitmap? = null,
    val hash: String = "",
    val extraction: ExtractedSchedule? = null,
    val confirmedMonday: LocalDate? = null,
    val calendars: List<WritableCalendar> = emptyList(),
    val prepared: PreparedSync? = null,
    val selectedChanges: Set<LocalDate> = emptySet(),
    val duplicatePhoto: Boolean = false,
    val syncCount: Int? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TurniApplication
    private val dao = app.database.dao()
    private val images = ImageProcessor(app)
    private val extractor: ScheduleExtractor = LocalScheduleExtractor(images)
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private var upcomingJob: Job? = null

    init {
        launchWork {
            val profile = dao.profile()
            mutable.update {
                it.copy(
                    loaded = true,
                    profile = profile,
                    settings =
                        dao.settings()
                            ?: SettingsEntity(title = app.getString(R.string.default_event_title)),
                )
            }
            profile?.let { watch(it.id) }
        }
    }

    private fun watch(employee: String) {
        upcomingJob?.cancel()
        upcomingJob = viewModelScope.launch {
            dao.upcoming(employee, LocalDate.now().toString()).collect { value ->
                mutable.update { it.copy(upcoming = value) }
            }
        }
    }

    private fun launchWork(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.update { it.copy(error = errorText(error)) }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    private fun errorText(error: Exception): String =
        app.getString(
            when {
                error is SecurityException -> R.string.permission_denied
                error.message.orEmpty().startsWith("DST_AMBIGUOUS") -> R.string.dst_error
                error.message == "REVIEW_REQUIRED" || error.message == "UNKNOWN_SHIFT" ->
                    R.string.review_required
                error.message == "INVALID_CROP" -> R.string.invalid_crop
                error.message == "CALENDAR_UNAVAILABLE" -> R.string.no_calendar
                error.message == "CALENDAR_OWNERSHIP_CHANGED" ||
                    error.message == "MULTIPLE_CALENDAR_MATCHES" -> R.string.calendar_conflict
                else -> R.string.operation_failed
            }
        )

    fun dismissError() {
        mutable.update { it.copy(error = null) }
    }

    fun reportError(message: String) {
        mutable.update { it.copy(error = message) }
    }

    fun saveName(name: String, onSaved: () -> Unit) = launchWork {
        require(name.isNotBlank() && name.trim().length <= 160)
        val profile =
            ProfileEntity(
                mutable.value.profile?.id ?: UUID.randomUUID().toString(),
                name.trim(),
                "",
            )
        dao.saveProfile(profile)
        mutable.update { it.copy(profile = profile) }
        watch(profile.id)
        onSaved()
    }

    fun loadImage(uri: Uri, capturedFile: java.io.File? = null, onLoaded: () -> Unit) = launchWork {
        try {
            val bitmap = images.decode(uri)
            clearImages()
            mutable.update {
                it.copy(
                    image = bitmap,
                    extraction = null,
                    prepared = null,
                    syncCount = null,
                    confirmedMonday = null,
                )
            }
            onLoaded()
        } finally {
            capturedFile?.delete()
        }
    }

    fun analyze(corners: List<Corner>, onFinished: () -> Unit) = launchWork {
        val original = requireNotNull(mutable.value.image)
        val rectified = images.rectify(original, corners)
        val profile = requireNotNull(mutable.value.profile)
        val hash = images.fingerprint(original)
        val result =
            extractor.extract(
                ImageInput(
                    rectified,
                    EmployeeProfile(profile.name),
                    LocalDate.now(),
                )
            )
        val duplicate = dao.byHash(profile.id, hash) != null
        mutable.update {
            it.copy(
                analyzed = rectified,
                extraction = result,
                hash = hash,
                duplicatePhoto = duplicate,
                prepared = null,
                confirmedMonday = null,
            )
        }
        onFinished()
    }

    fun chooseRow(row: RowCandidate) {
        reparse(row, mutable.value.confirmedMonday)
    }

    fun confirmMonday(value: String) {
        val monday = runCatching { LocalDate.parse(value) }.getOrNull()
        if (monday == null || monday.dayOfWeek.value != 1) {
            reportError(app.getString(R.string.invalid_monday))
            return
        }
        reparse(mutable.value.extraction?.table?.selected, monday)
    }

    private fun reparse(row: RowCandidate?, monday: LocalDate?) {
        val old = mutable.value.extraction ?: return
        val profile = mutable.value.profile ?: return
        val table =
            TableParser()
                .extract(
                    old.tokens,
                    EmployeeProfile(profile.name),
                    LocalDate.now(),
                    row,
                    monday,
                )
        // Explicit confirmation replaces conflicting OCR dates; warnings remain visible until this
        // action.
        val corrected =
            if (monday != null) table.copy(issues = table.issues - ExtractionIssue.INVALID_DATES)
            else table
        mutable.update {
            it.copy(
                extraction = old.copy(table = corrected),
                confirmedMonday = monday,
                prepared = null,
            )
        }
    }

    fun editCell(index: Int, date: String, raw: String): Boolean {
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        val cell =
            ShiftParser.parse(raw, parsedDate)
                .copy(
                    reviewed = true,
                    source = mutable.value.extraction?.table?.cells?.getOrNull(index)?.source,
                )
        if (cell.type == CellType.UNKNOWN) return false
        val old = mutable.value.extraction ?: return false
        val cells = old.table.cells.toMutableList()
        if (index in cells.indices) cells[index] = cell else cells.add(cell)
        if (cells.map { it.date }.distinct().size != cells.size) return false
        val monday =
            mutable.value.confirmedMonday
                ?: old.table.cells.firstOrNull()?.date?.with(java.time.DayOfWeek.MONDAY)
        if (monday != null && (parsedDate < monday || parsedDate > monday.plusDays(6))) return false
        mutable.update {
            it.copy(
                extraction =
                    old.copy(table = old.table.copy(cells = cells.sortedBy(ShiftCell::date))),
                prepared = null,
            )
        }
        return true
    }

    fun removeCell(index: Int) {
        val old = mutable.value.extraction ?: return
        mutable.update {
            it.copy(
                extraction =
                    old.copy(
                        table =
                            old.table.copy(
                                cells = old.table.cells.filterIndexed { i, _ -> i != index }
                            )
                    ),
                prepared = null,
            )
        }
    }

    fun acknowledge(index: Int) {
        val old = mutable.value.extraction ?: return
        mutable.update {
            it.copy(
                extraction =
                    old.copy(
                        table =
                            old.table.copy(
                                cells =
                                    old.table.cells.mapIndexed { i, cell ->
                                        if (i == index && cell.type != CellType.UNKNOWN)
                                            cell.copy(reviewed = true)
                                        else cell
                                    }
                            )
                    )
            )
        }
    }

    fun loadCalendars(onLoaded: () -> Unit) = launchWork {
        mutable.update { it.copy(calendars = app.calendar.calendars()) }
        onLoaded()
    }

    fun prepare(calendarId: Long, onPrepared: () -> Unit) = launchWork {
        val state = mutable.value
        val profile = requireNotNull(state.profile)
        val result = requireNotNull(state.extraction).table
        require(result.selected != null && result.issues.isEmpty()) { "REVIEW_REQUIRED" }
        require(result.cells.all { !it.needsReview || it.reviewed }) { "REVIEW_REQUIRED" }
        val monday =
            state.confirmedMonday
                ?: result.cells.firstOrNull()?.date?.with(java.time.DayOfWeek.MONDAY)
                ?: throw IllegalArgumentException("REVIEW_REQUIRED")
        val events =
            result.cells.mapNotNull {
                EventFactory.create(
                    it,
                    emptyList(),
                    EventOptions(
                        state.settings.title,
                        state.settings.location,
                        state.settings.description,
                        state.settings.reminderMinutes,
                        state.settings.eventColor,
                    ),
                    ZoneId.systemDefault(),
                )
            }
        val fullCells =
            (0L..6L).map { day ->
                result.cells.firstOrNull { it.date == monday.plusDays(day) }
                    ?: ShiftParser.parse("", monday.plusDays(day))
            }
        val plan = app.sync.prepare(profile.id, monday, calendarId, state.hash, fullCells, events)
        val settings = state.settings.copy(calendarId = calendarId)
        dao.saveSettings(settings)
        mutable.update {
            it.copy(
                prepared = plan,
                settings = settings,
                selectedChanges =
                    plan.changes
                        .filter { c ->
                            c.kind == ChangeKind.INSERT || c.kind == ChangeKind.RECREATE
                        }
                        .map { c -> c.date }
                        .toSet(),
            )
        }
        onPrepared()
    }

    fun toggleChange(date: LocalDate) {
        mutable.update {
            it.copy(
                selectedChanges =
                    if (date in it.selectedChanges) it.selectedChanges - date
                    else it.selectedChanges + date
            )
        }
    }

    fun synchronize(onDone: () -> Unit) = launchWork {
        val plan = requireNotNull(mutable.value.prepared)
        val count = app.sync.commit(plan, mutable.value.selectedChanges)
        mutable.update {
            it.copy(
                syncCount = count,
                prepared = null,
                image = null,
                analyzed = null,
                extraction = null,
            )
        }
        clearTemporaryFiles()
        onDone()
    }

    fun clearImages() {
        mutable.update {
            it.copy(image = null, analyzed = null, extraction = null, prepared = null)
        }
        clearTemporaryFiles()
    }

    private fun clearTemporaryFiles() {
        app.cacheDir.listFiles()?.filter { it.name.startsWith("turni-") }?.forEach { it.delete() }
    }

    override fun onCleared() {
        clearTemporaryFiles()
        super.onCleared()
    }
}
