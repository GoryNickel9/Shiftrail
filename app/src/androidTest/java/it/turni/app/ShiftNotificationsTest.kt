package it.turni.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.data.*
import it.turni.core.*
import java.time.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ShiftNotificationsTest {
    @Test
    fun remindersScheduleDeliverOnceRejectStaleEditsAndCancelWhenDisabled() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as TurniApplication
        if (Build.VERSION.SDK_INT >= 33)
            instrumentation.uiAutomation.grantRuntimePermission(
                app.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        val dao = app.database.dao()
        val oldSettings = dao.settings() ?: SettingsEntity()
        val oldProfile = dao.profile()
        val profile = ProfileEntity("notification-test", "Test Notifiche", "")
        val start = LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0)
        val due =
            ExtraWorkEntity(
                "notification-due",
                profile.id,
                start.toLocalDate().toString(),
                start.toLocalTime().toString(),
                start.plusHours(1).toLocalTime().toString(),
                1,
            )
        val future =
            due.copy(id = "notification-future", date = start.toLocalDate().plusDays(2).toString())
        val manager = app.getSystemService(NotificationManager::class.java)
        val preferences = app.getSharedPreferences("shift-notifications", Context.MODE_PRIVATE)
        try {
            if (oldProfile != null)
                app.database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM profiles WHERE id = ?",
                    arrayOf(oldProfile.id),
                )
            dao.saveProfile(profile)
            dao.saveSettings(oldSettings.copy(notificationsEnabled = true, notificationMinutes = 0))
            dao.saveExtra(due)
            dao.saveExtra(future)
            app.notifications.refresh()
            val futureKey =
                workReminders(listOf(future.period()), 0, ZoneId.systemDefault()).single().key
            assertTrue(preferences.getStringSet("scheduled", emptySet())!!.contains(futureKey))
            val dueKey = workReminders(listOf(due.period()), 0, ZoneId.systemDefault()).single().key
            app.notifications.refresh(dueKey)
            val deliveryDeadline = System.currentTimeMillis() + 5000
            while (
                manager.activeNotifications.none { it.tag == dueKey } &&
                    System.currentTimeMillis() < deliveryDeadline
            ) delay(50)
            assertEquals(1, manager.activeNotifications.count { it.tag == dueKey })
            val delivered = manager.activeNotifications.single { it.tag == dueKey }
            assertEquals("Lavoro", delivered.notification.extras.getString("android.title"))
            manager.cancel(dueKey, 1)
            val cancelDeadline = System.currentTimeMillis() + 5_000
            while (
                manager.activeNotifications.any { it.tag == dueKey } &&
                    System.currentTimeMillis() < cancelDeadline
            ) delay(50)
            app.notifications.refresh(dueKey)
            assertFalse(manager.activeNotifications.any { it.tag == dueKey })
            dao.removeExtra(due.id, profile.id)
            app.notifications.refresh(dueKey)
            assertFalse(manager.activeNotifications.any { it.tag == dueKey })
            assertTrue(
                "Grant Alarms & reminders on the emulator before this test",
                app.notifications.canScheduleExactly(),
            )
            val liveStart = LocalDateTime.now().plusSeconds(15).withNano(0)
            val live =
                future.copy(
                    date = liveStart.toLocalDate().toString(),
                    startTime = liveStart.toLocalTime().toString(),
                    endTime = liveStart.plusHours(1).toLocalTime().toString(),
                )
            dao.saveExtra(live)
            val liveKey =
                workReminders(listOf(live.period()), 0, ZoneId.systemDefault()).single().key
            app.notifications.refresh()
            val deadline = System.currentTimeMillis() + 30_000
            while (
                manager.activeNotifications.none { it.tag == liveKey } &&
                    System.currentTimeMillis() < deadline
            ) delay(250)
            assertTrue(
                "AlarmManager must deliver through the manifest receiver",
                manager.activeNotifications.any { it.tag == liveKey },
            )
            dao.saveSettings(oldSettings.copy(notificationsEnabled = false))
            app.notifications.refresh(futureKey)
            val disableDeadline = System.currentTimeMillis() + 5_000
            while (
                manager.activeNotifications.any { it.tag == liveKey } &&
                    System.currentTimeMillis() < disableDeadline
            ) delay(50)
            assertTrue(preferences.getStringSet("scheduled", emptySet()).orEmpty().isEmpty())
            assertFalse(manager.activeNotifications.any { it.tag == futureKey })
            assertFalse(manager.activeNotifications.any { it.tag == liveKey })
        } finally {
            dao.removeExtra(due.id, profile.id)
            dao.removeExtra(future.id, profile.id)
            dao.saveSettings(oldSettings)
            app.database.openHelper.writableDatabase.execSQL(
                "DELETE FROM profiles WHERE id = ?",
                arrayOf(profile.id),
            )
            if (oldProfile != null) dao.saveProfile(oldProfile)
            app.notifications.refresh()
        }
    }

    @Test
    fun soundAndVibrationUseFourIndependentAndroidChannels() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as TurniApplication
        val ids = mutableSetOf<String>()
        for (sound in listOf(false, true)) for (vibration in listOf(false, true)) {
            val settings =
                SettingsEntity(notificationSound = sound, notificationVibration = vibration)
            val channel = app.notifications.ensureChannel(settings)
            assertEquals(sound, channel.sound != null)
            assertEquals(vibration, channel.shouldVibrate())
            ids += channel.id
        }
        assertEquals(4, ids.size)
    }
}
