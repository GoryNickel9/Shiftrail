package it.turni.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.data.ExtraWorkEntity
import it.turni.app.data.ProfileEntity
import it.turni.app.data.SettingsEntity
import it.turni.app.notifications.*
import it.turni.core.WorkReminder
import it.turni.core.workReminders
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ShiftAlarmUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun alarmRingsHasFullScreenIntentAndStopsFromItsScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as TurniApplication
        val dao = app.database.dao()
        val old = runBlocking { dao.settings() } ?: SettingsEntity()
        val manager = app.getSystemService(NotificationManager::class.java)
        val previousProfile = runBlocking { dao.profile() }
        val employee = previousProfile?.id ?: "alarm-test-profile"
        val date = LocalDate.now().plusDays(1)
        val extra = ExtraWorkEntity("alarm-preview", employee, date.toString(), "06:00", "07:00", 1)
        val alarm = workReminders(listOf(extra.period()), 0, ZoneId.systemDefault()).single()
        try {
            runBlocking {
                if (previousProfile == null)
                    dao.saveProfile(ProfileEntity(employee, "Test sveglia", ""))
                dao.saveExtra(extra)
                dao.saveSettings(
                    old.copy(
                        notificationsEnabled = true,
                        notificationMinutes = 0,
                        fullScreenAlarm = true,
                        notificationSound = true,
                        notificationVibration = false,
                    )
                )
            }
            compose.waitUntil(15000) { app.notifications.canNotify() }
            ShiftAlarmService.start(app, alarm)
            compose.waitUntil(15000) { ShiftAlarmState.active.value?.key == alarm.key }
            compose.waitUntil(15000) {
                manager.activeNotifications.any { it.id == ShiftAlarmService.NOTIFICATION_ID }
            }
            val notification =
                manager.activeNotifications
                    .single { it.id == ShiftAlarmService.NOTIFICATION_ID }
                    .notification
            assertEquals(
                it.turni.core.workTimeRange(alarm.start, alarm.end),
                notification.extras.getString("android.title"),
            )
            assertNull(notification.extras.getString("android.text"))
            assertFalse(notification.extras.getBoolean("android.showWhen"))
            if (app.notifications.canUseFullScreenAlarm())
                assertNotNull(notification.fullScreenIntent)
            else assertNull(notification.fullScreenIntent)
            compose.waitUntil(15000) {
                app.getSystemService(AudioManager::class.java).activePlaybackConfigurations.any {
                    it.audioAttributes.usage == AudioAttributes.USAGE_ALARM
                }
            }
            notification.contentIntent.send()
            compose.onNodeWithTag("stop_shift_alarm").assertIsDisplayed()
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val dir = File(app.getExternalFilesDir(null), "qa-alarm").apply { mkdirs() }
            File(dir, "alarm.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            compose.onNodeWithTag("stop_shift_alarm").performClick()
            compose.waitUntil(15000) { ShiftAlarmState.active.value == null }
            compose.waitUntil(15000) {
                manager.activeNotifications.none { it.id == ShiftAlarmService.NOTIFICATION_ID }
            }
        } finally {
            app.stopService(Intent(app, ShiftAlarmService::class.java))
            runBlocking {
                dao.removeExtra(extra.id, employee)
                dao.saveSettings(old)
                if (previousProfile == null)
                    app.database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM profiles WHERE id = ?",
                        arrayOf(employee),
                    )
            }
        }
    }

    @Test
    fun exactAlarmOpensAutomaticallyOverTheLockScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as TurniApplication
        org.junit.Assume.assumeTrue(
            app.notifications.canScheduleExactly() && app.notifications.canUseFullScreenAlarm()
        )
        val dao = app.database.dao()
        val old = runBlocking { dao.settings() } ?: SettingsEntity()
        val previousProfile = runBlocking { dao.profile() }
        val employee = "locked-alarm-test"
        val start = LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0)
        val extra =
            ExtraWorkEntity(
                "locked-alarm-extra",
                employee,
                start.toLocalDate().toString(),
                start.toLocalTime().toString(),
                start.plusHours(1).toLocalTime().toString(),
                1,
            )
        val alarm = workReminders(listOf(extra.period()), 0, ZoneId.systemDefault()).single()
        val operation =
            PendingIntent.getBroadcast(
                app,
                12,
                Intent(app, ShiftReminderReceiver::class.java)
                    .setAction("it.turni.app.WORK_REMINDER")
                    .setData(Uri.parse("shiftrail://reminder/${alarm.key}")),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        try {
            runBlocking {
                if (previousProfile != null)
                    app.database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM profiles WHERE id = ?",
                        arrayOf(previousProfile.id),
                    )
                dao.saveProfile(ProfileEntity(employee, "Test sveglia bloccata", ""))
                dao.saveExtra(extra)
                dao.saveSettings(
                    old.copy(
                        notificationsEnabled = true,
                        notificationMinutes = 0,
                        fullScreenAlarm = true,
                        notificationSound = false,
                        notificationVibration = false,
                    )
                )
                app.notifications.refresh()
            }
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1800,
                operation,
            )
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            compose.waitUntil(20000) {
                ShiftAlarmState.active.value?.key == alarm.key &&
                    app.getSystemService(PowerManager::class.java).isInteractive
            }
            compose.onNodeWithTag("stop_shift_alarm").assertIsDisplayed()
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val dir = File(app.getExternalFilesDir(null), "qa-alarm").apply { mkdirs() }
            File(dir, "locked-alarm.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            compose.onNodeWithTag("stop_shift_alarm").performClick()
            compose.waitUntil(15000) { ShiftAlarmState.active.value == null }
        } finally {
            alarmManager.cancel(operation)
            app.stopService(Intent(app, ShiftAlarmService::class.java))
            instrumentation.uiAutomation
                .executeShellCommand("input keyevent KEYCODE_WAKEUP")
                .close()
            instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
            runBlocking {
                dao.removeExtra(extra.id, employee)
                app.database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM profiles WHERE id = ?",
                    arrayOf(employee),
                )
                if (previousProfile != null) dao.saveProfile(previousProfile)
                dao.saveSettings(old)
                app.notifications.refresh()
            }
        }
    }

    @Test
    fun unsavedOrDisabledAlarmCannotKeepRinging() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val dao = app.database.dao()
        val old = runBlocking { dao.settings() } ?: SettingsEntity()
        try {
            runBlocking {
                dao.saveSettings(old.copy(notificationsEnabled = true, fullScreenAlarm = false))
            }
            val now = System.currentTimeMillis()
            ShiftAlarmService.start(app, WorkReminder("$now-0", now, now + 600_000, now))
            Thread.sleep(500)
            compose.waitUntil(15000) {
                ShiftAlarmState.active.value == null &&
                    app.getSystemService(NotificationManager::class.java).activeNotifications.none {
                        it.id == ShiftAlarmService.NOTIFICATION_ID
                    }
            }
        } finally {
            app.stopService(Intent(app, ShiftAlarmService::class.java))
            runBlocking { dao.saveSettings(old) }
        }
    }
}
