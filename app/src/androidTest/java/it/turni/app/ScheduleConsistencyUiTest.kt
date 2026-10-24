package it.turni.app

import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.CalendarContract
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.calendar.CalendarMutation
import it.turni.app.data.*
import it.turni.app.ui.AppViewModel
import it.turni.app.ui.TurniApp
import it.turni.core.*
import java.io.File
import java.time.*
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScheduleConsistencyUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun repeatedImportsDoNotCutTheWeekAndCalendarChangesReachBothScreens() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val dao = app.database.dao()
        val oldProfile = runBlocking { dao.profile() }
        val profile = oldProfile ?: ProfileEntity("schedule-consistency", "Test Schedule", "")
        val monday =
            YearMonth.now()
                .plusMonths(1)
                .atDay(1)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        val weekId = "consistency-week"
        val zone = ZoneId.systemDefault()
        CalendarFixture("consistent-calendar").use { fixture ->
            val ids = runBlocking {
                if (oldProfile == null) dao.saveProfile(profile)
                dao.saveWeek(WeekEntity(weekId, profile.id, monday.toString(), 1, "consistency"))
                val mutations =
                    (0L..6L).map { day ->
                        val cell =
                            ShiftParser.parse(
                                if (day == 6L) "12:30-15:30" else "09-17",
                                monday.plusDays(day),
                            )
                        CalendarMutation(
                            cell.date,
                            requireNotNull(
                                EventFactory.create(cell, emptyList(), EventOptions(), zone)
                            ),
                            null,
                            eventMarker(profile.id, cell.date, fixture.id),
                        )
                    }
                val written = app.calendar.apply(fixture.id, mutations)
                for (day in 0L..6L) for (version in 0L..2L) {
                    dao.saveShift(
                        ShiftEntity(
                            "consistency-$day-$version",
                            weekId,
                            profile.id,
                            monday.plusDays(day).toString(),
                            "08:00",
                            "16:00",
                            false,
                            null,
                            "WORK_SHIFT",
                            1.0,
                            requireNotNull(written[monday.plusDays(day)]),
                            if (version == 2L) fixture.id else fixture.id + 10000 + version,
                            "cached",
                            1,
                            9999999999999L + version,
                        )
                    )
                }
                written
            }
            val vm = AppViewModel(app)
            val store = ViewModelStore().apply { put("consistency", vm) }
            try {
                compose.setContent { TurniApp(vm) }
                compose.waitUntil(15000) {
                    vm.state.value.upcoming.count { it.id.startsWith("consistency-") } == 7
                }
                val sunday = monday.plusDays(6)
                val latest = vm.state.value.upcoming.filter { it.id.startsWith("consistency-") }
                assertEquals(7, latest.size)
                assertEquals(sunday.toString(), latest.last().date)
                assertEquals("15:30", latest.last().endTime)
                assertEquals(
                    7,
                    vm.state.value.hoursPeriods.count { it.id.startsWith("consistency-") },
                )
                compose
                    .onNode(hasScrollToIndexAction())
                    .performScrollToNode(hasTestTag("home_shift_consistency-6-2"))
                compose.onNodeWithText("12:30 → 15:30").assertIsDisplayed()
                File(app.getExternalFilesDir(null), "qa-home-week.png").outputStream().use {
                    compose
                        .onRoot()
                        .captureToImage()
                        .asAndroidBitmap()
                        .compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                val movedDate = sunday.plusDays(1)
                app.contentResolver.update(
                    ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_URI,
                        requireNotNull(ids[sunday]),
                    ),
                    ContentValues().apply {
                        put(
                            CalendarContract.Events.DTSTART,
                            movedDate.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                        )
                        put(
                            CalendarContract.Events.DTEND,
                            movedDate.atTime(13, 0).atZone(zone).toInstant().toEpochMilli(),
                        )
                    },
                    null,
                    null,
                )
                compose.waitUntil(15000) {
                    vm.state.value.upcoming.any {
                        it.id == "consistency-6-2" &&
                            it.date == movedDate.toString() &&
                            it.endTime == "13:00"
                    } &&
                        vm.state.value.hoursPeriods.any {
                            it.id == "consistency-6-2" &&
                                it.date == movedDate &&
                                it.end == LocalTime.of(13, 0)
                        }
                }
                assertEquals(
                    sunday.toString(),
                    runBlocking { dao.shift("consistency-6-2", profile.id) }!!.date,
                )
                compose
                    .onNode(hasScrollToIndexAction())
                    .performScrollToNode(hasTestTag("home_shift_consistency-6-2"))
                compose.onNodeWithText("10:00 → 13:00").assertIsDisplayed()
            } finally {
                compose.runOnIdle { store.clear() }
                runBlocking {
                    app.database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM weeks WHERE id = ?",
                        arrayOf(weekId),
                    )
                    if (oldProfile == null)
                        app.database.openHelper.writableDatabase.execSQL(
                            "DELETE FROM profiles WHERE id = ?",
                            arrayOf(profile.id),
                        )
                }
            }
        }
    }
}
