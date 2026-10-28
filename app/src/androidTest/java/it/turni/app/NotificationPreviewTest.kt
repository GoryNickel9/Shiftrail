package it.turni.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import it.turni.app.data.*
import it.turni.core.workReminders
import java.io.File
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Captures real Android UI with a synthetic 22:00–06:00 work day. */
class NotificationPreviewTest {
    @Test
    fun captureIconStatusBarNotificationAndUnreadLauncher() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as TurniApplication
        val dao = app.database.dao()
        val oldSettings = dao.settings() ?: SettingsEntity()
        val oldProfile = dao.profile()
        val profile = ProfileEntity("preview-notification", "Esempio Shiftrail", "")
        if (Build.VERSION.SDK_INT >= 33)
            instrumentation.uiAutomation.grantRuntimePermission(
                app.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        val now = LocalDateTime.now()
        val start = now.toLocalDate().atTime(22, 0).let { if (it <= now) it.plusDays(1) else it }
        val extra =
            ExtraWorkEntity(
                "preview-notification-extra",
                profile.id,
                start.toLocalDate().toString(),
                "22:00",
                "06:00",
                1,
            )
        val minutes = ((Duration.between(now, start).toMinutes().toInt() + 5) / 5) * 5
        fun screenshot(name: String, region: Rect? = null) {
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val visible =
                if (region == null) bitmap
                else
                    Bitmap.createBitmap(
                        bitmap,
                        region.left,
                        region.top,
                        region.width(),
                        region.height(),
                    )
            val directory = File(app.getExternalFilesDir(null), "qa-1.0.4").apply { mkdirs() }
            File(directory, name).outputStream().use {
                visible.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            if (visible !== bitmap) visible.recycle()
            bitmap.recycle()
        }
        try {
            if (oldProfile != null)
                app.database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM profiles WHERE id = ?",
                    arrayOf(oldProfile.id),
                )
            dao.saveProfile(profile)
            dao.saveSettings(
                oldSettings.copy(
                    notificationsEnabled = true,
                    fullScreenAlarm = false,
                    notificationMinutes = minutes,
                    notificationSound = false,
                    notificationVibration = false,
                )
            )
            dao.saveExtra(extra)
            val icon = app.packageManager.getApplicationIcon(app.packageName)
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, 512, 512)
            icon.draw(Canvas(bitmap))
            File(app.filesDir, "shiftrail-icon.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            val key =
                workReminders(listOf(extra.period()), minutes, ZoneId.systemDefault()).single().key
            app.notifications.refresh(key)
            val manager = app.getSystemService(NotificationManager::class.java)
            val deadline = SystemClock.uptimeMillis() + 5000
            while (
                manager.activeNotifications.none { it.tag == key } &&
                    SystemClock.uptimeMillis() < deadline
            ) SystemClock.sleep(50)
            assertTrue(
                app.getSystemService(NotificationManager::class.java).activeNotifications.any {
                    it.tag == key
                }
            )
            app.startActivity(
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            SystemClock.sleep(1500)
            screenshot("shiftrail-status-unread.png")
            assertTrue(
                instrumentation.uiAutomation.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                )
            )
            SystemClock.sleep(1200)
            screenshot("shiftrail-notification.png")
            val display = app.resources.displayMetrics
            if (display.widthPixels == 1080 && display.heightPixels == 2400) {
                // Frame measured on the API 35 fixture; keep the full screenshot as evidence.
                screenshot("shiftrail-notification-detail.png", Rect(40, 600, 1040, 980))
            }
            assertTrue(
                instrumentation.uiAutomation.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_HOME
                )
            )
            SystemClock.sleep(1000)
            val metrics = app.resources.displayMetrics
            instrumentation.uiAutomation
                .executeShellCommand(
                    "input swipe ${metrics.widthPixels / 2} ${metrics.heightPixels * 9 / 10} ${metrics.widthPixels / 2} ${metrics.heightPixels / 5} 400"
                )
                .close()
            SystemClock.sleep(1200)
            val icons =
                instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("Shiftrail")
                    .orEmpty()
            assertTrue("Shiftrail must be visible in the launcher", icons.isNotEmpty())
            android.util.Log.i(
                "ShiftrailPreview",
                "Launcher icon: ${icons.map { it.contentDescription }}",
            )
            screenshot("shiftrail-launcher-unread.png")
            val iconBounds = Rect()
            icons.first().getBoundsInScreen(iconBounds)
            screenshot("shiftrail-icon-unread.png", iconBounds)
        } finally {
            dao.removeExtra(extra.id, profile.id)
            dao.saveSettings(oldSettings)
            app.database.openHelper.writableDatabase.execSQL(
                "DELETE FROM profiles WHERE id = ?",
                arrayOf(profile.id),
            )
            if (oldProfile != null) dao.saveProfile(oldProfile)
            app.notifications.refresh()
            instrumentation.uiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_HOME
            )
        }
    }
}
