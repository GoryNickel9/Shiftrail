package it.turni.app.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import it.turni.app.BuildConfig
import it.turni.app.R
import it.turni.app.TurniApplication
import it.turni.app.calendar.*
import it.turni.app.data.*
import it.turni.app.imageprocessing.*
import it.turni.app.ocr.*
import it.turni.app.update.UpdateState
import it.turni.core.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
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
    val syncedCalendar: WritableCalendar? = null,
    val syncedShift: ShiftEntity? = null,
    val hoursPeriods: List<WorkPeriod> = emptyList(),
    val hoursExtras: List<ExtraWorkEntity> = emptyList(),
    val hoursLoaded: Boolean = false,
    val hoursCalendarAvailable: Boolean = false,
    val updater: UpdateState = UpdateState(),
    val background: Bitmap? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TurniApplication
    private val dao = app.database.dao()
    private val images = ImageProcessor(app)
    private val backgrounds = AppBackground(app)
    private val extractor: ScheduleExtractor = LocalScheduleExtractor(images)
    private val mutable =
        MutableStateFlow(
            AppState(
                updater =
                    UpdateState(
                        release = app.updater.requiredUpdate(),
                        betaEnabled = app.updater.betaEnabled(),
                        betaRelease = app.updater.betaUpdate(),
                    )
            )
        )
    val state = mutable.asStateFlow()
    private var hoursJob: Job? = null
    private var hoursMonth = YearMonth.now()
    private val calendarRefresh = MutableStateFlow(0)
    private var updateJob: Job? = null

    fun refreshCalendar() {
        app.notifications.requestRefresh()
        if (hoursJob?.isCompleted == true) mutable.value.profile?.let { watchHours(it.id) }
        else calendarRefresh.update { it + 1 }
    }

    fun checkUpdates(automatic: Boolean = false) {
        if (updateJob?.isActive == true || mutable.value.updater.downloading) return
        updateJob = viewModelScope.launch {
            mutable.update { it.copy(updater = it.updater.copy(checking = true, message = null)) }
            try {
                val latest = app.updater.check()
                val release = app.updater.requiredUpdate()
                // A beta endpoint failure must not discard a successfully checked stable release.
                var betaFailed = false
                val beta =
                    try {
                        app.updater.checkBeta(force = !automatic)
                        app.updater.betaUpdate()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        betaFailed = true
                        app.updater.betaUpdate()
                    }
                mutable.update {
                    it.copy(
                        updater =
                            it.updater.copy(
                                release = release,
                                betaRelease = beta,
                                checking = false,
                                ready =
                                    it.updater.ready &&
                                        it.updater.downloadedRelease != null &&
                                        it.updater.downloadedRelease in listOf(release, beta),
                                message =
                                    if (release == null && betaFailed)
                                        app.getString(R.string.beta_check_failed)
                                    else if (release == null && beta == null && !automatic)
                                        app.getString(
                                            when {
                                                latest == null -> R.string.update_no_release
                                                latest.versionCode > BuildConfig.VERSION_CODE &&
                                                    latest.minSdk > Build.VERSION.SDK_INT ->
                                                    R.string.update_incompatible
                                                else -> R.string.update_current
                                            }
                                        )
                                    else null,
                            )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update {
                    it.copy(
                        updater =
                            it.updater.copy(
                                checking = false,
                                message =
                                    if (automatic && it.updater.release == null) null
                                    else app.getString(R.string.update_check_failed),
                            )
                    )
                }
            }
        }
    }

    fun setBetaParticipation(enabled: Boolean) {
        if (mutable.value.updater.downloading) return
        updateJob?.cancel()
        updateJob = null
        app.updater.setBetaEnabled(enabled)
        mutable.update {
            it.copy(
                updater =
                    it.updater.copy(
                        betaEnabled = enabled,
                        betaRelease = null,
                        checking = false,
                        message = null,
                        ready = false,
                        downloadedRelease = null,
                    )
            )
        }
        if (enabled) checkUpdates()
    }

    fun downloadUpdate() = downloadRelease(mutable.value.updater.release)

    fun downloadBetaUpdate() = downloadRelease(mutable.value.updater.betaRelease)

    private fun downloadRelease(release: ReleaseUpdate?) {
        if (updateJob?.isActive == true) return
        if (release == null || (release.isBeta && !app.updater.betaEnabled())) return
        updateJob = viewModelScope.launch {
            mutable.update {
                it.copy(
                    updater =
                        it.updater.copy(
                            downloading = true,
                            ready = false,
                            downloadedRelease = null,
                            message = null,
                        )
                )
            }
            try {
                app.updater.download(release)
                mutable.update {
                    it.copy(
                        updater =
                            it.updater.copy(
                                downloading = false,
                                ready = true,
                                downloadedRelease = release,
                            )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update {
                    it.copy(
                        updater =
                            it.updater.copy(
                                downloading = false,
                                message = app.getString(R.string.update_download_failed),
                            )
                    )
                }
            }
        }
    }

    fun installUpdate() {
        try {
            val updater = mutable.value.updater
            check(updater.ready && updater.downloadedRelease != null)
            check(updater.release == null || updater.downloadedRelease == updater.release)
            check(!updater.downloadedRelease.isBeta || app.updater.betaEnabled())
            app.startActivity(app.updater.installIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            mutable.update {
                it.copy(
                    updater =
                        it.updater.copy(
                            ready = false,
                            message = app.getString(R.string.update_install_failed),
                        )
                )
            }
        }
    }

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
                    calendars =
                        if (hasCalendarPermission()) app.calendar.calendars() else emptyList(),
                )
            }
            profile?.let { watch(it.id) }
            try {
                val background = backgrounds.load()
                mutable.update { it.copy(background = background) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                reportError(app.getString(R.string.background_failed))
            }
        }
    }

    private fun watch(employee: String) {
        watchHours(employee)
    }

    private fun hasCalendarPermission() =
        ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private data class ScheduleSnapshot(
        val upcoming: List<ShiftEntity>,
        val periods: List<WorkPeriod>,
        val extras: List<ExtraWorkEntity>,
        val calendarAvailable: Boolean,
    )

    fun loadHours(month: YearMonth) {
        if (month == hoursMonth && hoursJob?.isActive == true) return
        hoursMonth = month
        mutable.value.profile?.let { watchHours(it.id) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchHours(employee: String) {
        hoursJob?.cancel()
        mutable.update {
            it.copy(hoursLoaded = false, hoursPeriods = emptyList(), hoursExtras = emptyList())
        }
        hoursJob = viewModelScope.launch {
            combine(
                    dao.allRecordedShifts(employee),
                    dao.allExtraWork(employee),
                    calendarRefresh.flatMapLatest {
                        if (
                            ContextCompat.checkSelfPermission(
                                app,
                                Manifest.permission.READ_CALENDAR,
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                            app.calendar.changes().map { true }
                        else flowOf(false)
                    },
                ) { shifts, extras, calendarAvailable ->
                    // A calendar change must not count the same employee/day twice.
                    val current =
                        shifts
                            .groupBy { it.date }
                            .values
                            .map { versions ->
                                versions.maxWith(
                                    compareBy<ShiftEntity> { it.updatedAt }.thenBy { it.id }
                                )
                            }
                    val schedule =
                        if (calendarAvailable)
                            calendarSchedule(
                                current,
                                app.calendar.events(current.map { it.calendarEventId }),
                                ZoneId.systemDefault(),
                                extras,
                            )
                        else
                            CalendarSchedule(
                                current
                                    .map { extraHoursSummary(it, extras) }
                                    .filter { it.type != CellType.EMPTY.name },
                                current
                                    .filter { it.type == CellType.WORK_SHIFT.name }
                                    .map {
                                        WorkPeriod(
                                            it.id,
                                            LocalDate.parse(it.date),
                                            LocalTime.parse(requireNotNull(it.startTime)),
                                            LocalTime.parse(requireNotNull(it.endTime)),
                                            WorkSource.SCHEDULE,
                                        )
                                    },
                            )
                    ScheduleSnapshot(
                        schedule.shifts
                            .filter { LocalDate.parse(it.date) >= LocalDate.now() }
                            .sortedWith(compareBy({ it.date }, { it.startTime.orEmpty() })),
                        schedule.periods + extras.map { it.period() },
                        extras,
                        calendarAvailable,
                    )
                }
                .catch { error ->
                    mutable.update {
                        it.copy(
                            hoursLoaded = true,
                            error = errorText(error as? Exception ?: Exception(error)),
                        )
                    }
                }
                .collect { (upcoming, periods, extras, calendarAvailable) ->
                    app.notifications.requestRefresh()
                    mutable.update {
                        it.copy(
                            hoursPeriods = periods,
                            upcoming = upcoming,
                            hoursExtras = extras,
                            hoursLoaded = true,
                            hoursCalendarAvailable = calendarAvailable,
                        )
                    }
                }
        }
    }

    fun saveExtra(
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        previous: ExtraWorkEntity?,
        onSaved: () -> Unit,
    ) = launchWork {
        val employee = requireNotNull(mutable.value.profile).id
        require(start != end)
        require(previous == null || previous.employeeId == employee)
        app.sync.saveExtra(
            ExtraWorkEntity(
                previous?.id ?: UUID.randomUUID().toString(),
                employee,
                date.toString(),
                start.toString(),
                end.toString(),
                previous?.createdAt ?: System.currentTimeMillis(),
            ),
            previous,
            mutable.value.settings.calendarId,
            extraEventOptions(),
            ZoneId.systemDefault(),
        )
        onSaved()
    }

    fun saveShiftHours(period: WorkPeriod, start: LocalTime, end: LocalTime, onSaved: () -> Unit) =
        launchWork {
            require(period.source == WorkSource.SCHEDULE)
            require(period.endDateTime < period.startDateTime.plusDays(1)) {
                "CALENDAR_EDIT_DURATION"
            }
            val state = mutable.value
            app.sync.updateHours(
                requireNotNull(state.profile).id,
                period.id,
                start,
                end,
                EventOptions(
                    location = state.settings.location,
                    description = state.settings.description,
                    reminderMinutes = state.settings.reminderMinutes,
                    color = state.settings.eventColor,
                ),
                ZoneId.systemDefault(),
            )
            onSaved()
        }

    fun removeExtra(extra: ExtraWorkEntity, onRemoved: () -> Unit) = launchWork {
        val employee = requireNotNull(mutable.value.profile).id
        require(extra.employeeId == employee)
        app.sync.removeExtra(extra, extraEventOptions(), ZoneId.systemDefault())
        onRemoved()
    }

    private fun extraEventOptions() =
        mutable.value.settings.let {
            EventOptions(
                location = it.location,
                description = it.description,
                reminderMinutes = it.reminderMinutes,
                color = it.eventColor,
                restColor = it.restColor,
                saveRestDays = it.saveRestDays,
            )
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
                error.message == "CALENDAR_NOT_WRITABLE" -> R.string.calendar_not_writable
                error.message == "CALENDAR_SYNC_DISABLED" -> R.string.calendar_sync_disabled
                error.message == "CALENDAR_ENABLE_FAILED" -> R.string.calendar_enable_failed
                error.message == "CALENDAR_WRITE_FAILED" -> R.string.calendar_write_failed
                error.message == "CALENDAR_EDIT_DURATION" -> R.string.calendar_long_event
                error.message == "EXTRA_DUPLICATE" -> R.string.extra_duplicate
                error.message == "CALENDAR_EXTRA_CONFLICT" || error.message == "EXTRA_CHANGED" ->
                    R.string.extra_calendar_conflict
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

    fun saveSettings(name: String, settings: SettingsEntity, onSaved: () -> Unit) = launchWork {
        require(name.isNotBlank() && name.trim().length <= 160)
        require(settings.reminderMinutes == null || settings.reminderMinutes in 0..10080)
        require(settings.notificationMinutes in 0..10080)
        val profile =
            ProfileEntity(
                mutable.value.profile?.id ?: UUID.randomUUID().toString(),
                name.trim(),
                "",
            )
        val savedSettings = settings.copy(title = EventFactory.WORK_EVENT_TITLE)
        app.database.withTransaction {
            dao.saveProfile(profile)
            dao.saveSettings(savedSettings)
        }
        mutable.update { it.copy(profile = profile, settings = savedSettings) }
        watch(profile.id)
        onSaved()
    }

    fun setBackground(uri: Uri?) = launchWork {
        try {
            val background =
                if (uri == null) {
                    backgrounds.clear()
                    null
                } else backgrounds.save(uri)
            mutable.update { it.copy(background = background) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            reportError(app.getString(R.string.background_failed))
        }
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

    fun saveCalendar(calendarId: Long, enable: Boolean, onSaved: () -> Unit) = launchWork {
        if (enable) app.calendar.enableForImport(calendarId)
        val calendars = app.calendar.calendars()
        require(preferredCalendar(calendars, calendarId) != null) { "CALENDAR_UNAVAILABLE" }
        val settings = mutable.value.settings.copy(calendarId = calendarId)
        dao.saveSettings(settings)
        mutable.update { it.copy(settings = settings, calendars = calendars) }
        onSaved()
    }

    fun continueImport(onPrepared: () -> Unit, onChooseCalendar: () -> Unit) = launchWork {
        val calendars = app.calendar.calendars()
        mutable.update { it.copy(calendars = calendars) }
        val preferred = preferredCalendar(calendars, mutable.value.settings.calendarId)
        if (preferred == null) onChooseCalendar()
        else {
            preparePlan(preferred.id, false)
            onPrepared()
        }
    }

    fun prepare(calendarId: Long, enable: Boolean = false, onPrepared: () -> Unit) = launchWork {
        preparePlan(calendarId, enable)
        onPrepared()
    }

    private suspend fun preparePlan(calendarId: Long, enable: Boolean) {
        val state = mutable.value
        val profile = requireNotNull(state.profile)
        val result = requireNotNull(state.extraction).table
        require(result.selected != null && result.issues.isEmpty()) { "REVIEW_REQUIRED" }
        require(result.cells.none { it.requiresCalendarReview(state.settings.saveRestDays) }) {
            "REVIEW_REQUIRED"
        }
        if (enable) app.calendar.enableForImport(calendarId)
        val calendars = app.calendar.calendars()
        val calendar = calendars.singleOrNull { it.id == calendarId }
        require(calendar != null) { "CALENDAR_UNAVAILABLE" }
        require(calendar.writable) { "CALENDAR_NOT_WRITABLE" }
        require(calendar.visible && calendar.syncEnabled) { "CALENDAR_SYNC_DISABLED" }
        val monday =
            state.confirmedMonday
                ?: result.cells.firstOrNull()?.date?.with(java.time.DayOfWeek.MONDAY)
                ?: throw IllegalArgumentException("REVIEW_REQUIRED")
        val options = extraEventOptions()
        val events =
            result.cells.mapNotNull {
                EventFactory.create(
                    it,
                    emptyList(),
                    options,
                    ZoneId.systemDefault(),
                )
            }
        val fullCells =
            (0L..6L).map { day ->
                result.cells.firstOrNull { it.date == monday.plusDays(day) }
                    ?: ShiftParser.parse("", monday.plusDays(day))
            }
        val plan =
            app.sync.prepare(profile.id, monday, calendarId, state.hash, fullCells, events, options)
        val settings = state.settings.copy(calendarId = calendarId)
        dao.saveSettings(settings)
        mutable.update {
            it.copy(
                prepared = plan,
                calendars = calendars,
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
        val shift =
            dao.previous(
                    plan.employee,
                    plan.monday.toString(),
                    plan.monday.plusDays(6).toString(),
                    plan.calendar,
                )
                .filter { saved ->
                    plan.changes.any { it.incoming != null && it.date.toString() == saved.date }
                }
                .minByOrNull { it.date }
        mutable.update {
            it.copy(
                syncCount = count,
                syncedCalendar =
                    it.calendars.firstOrNull { calendar -> calendar.id == plan.calendar },
                syncedShift = shift,
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
