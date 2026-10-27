package it.turni.app.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import it.turni.app.MainActivity
import it.turni.app.R
import it.turni.app.TurniApplication
import it.turni.app.calendar.calendarSchedule
import it.turni.app.data.SettingsEntity
import it.turni.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ShiftNotifications(private val app: TurniApplication) {
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val preferences = app.getSharedPreferences("shift-notifications", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    fun canNotify() =
        NotificationManagerCompat.from(app).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED)

    fun canScheduleExactly() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun channelId(settings: SettingsEntity) =
        "work-${settings.notificationSound}-${settings.notificationVibration}"

    fun ensureChannel(settings: SettingsEntity): NotificationChannel {
        val channel =
            NotificationChannel(
                channelId(settings),
                app.getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        channel.description = app.getString(R.string.notification_channel_description)
        channel.enableVibration(settings.notificationVibration)
        channel.setSound(
            if (settings.notificationSound)
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else null,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
        )
        manager.createNotificationChannel(channel)
        return manager.getNotificationChannel(channel.id)
    }

    fun requestRefresh() {
        WorkManager.getInstance(app)
            .enqueueUniqueWork(
                "shift-reminders-refresh",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderRefreshWorker>().build(),
            )
    }

    private fun pending(key: String): PendingIntent =
        PendingIntent.getBroadcast(
            app,
            0,
            Intent(app, ShiftReminderReceiver::class.java)
                .setAction("it.turni.app.WORK_REMINDER")
                .setData(Uri.parse("shiftrail://reminder/$key")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    suspend fun refresh(deliveryKey: String? = null) = mutex.withLock {
        val settings = app.database.dao().settings() ?: SettingsEntity()
        val work = WorkManager.getInstance(app)
        if (settings.notificationsEnabled) {
            work.enqueueUniquePeriodicWork(
                "shift-reminders-periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderRefreshWorker>(15, TimeUnit.MINUTES).build(),
            )
        } else work.cancelUniqueWork("shift-reminders-periodic")
        val previous = preferences.getStringSet("scheduled", emptySet()).orEmpty().toSet()
        if (!settings.notificationsEnabled || !canNotify()) {
            previous.forEach { alarms.cancel(pending(it)) }
            manager.cancelAll()
            preferences.edit().remove("scheduled").apply()
            return@withLock
        }
        ensureChannel(settings)
        val dao = app.database.dao()
        val employee = dao.profile()?.id
        val periods =
            if (employee == null) emptyList()
            else {
                val shifts =
                    dao.allRecordedShifts(employee)
                        .first()
                        .groupBy { it.date }
                        .values
                        .map {
                            it.maxWith(
                                compareBy({ shift -> shift.updatedAt }, { shift -> shift.id })
                            )
                        }
                val extras = dao.allExtraWork(employee).first()
                val calendarAccess =
                    ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALENDAR) ==
                        PackageManager.PERMISSION_GRANTED
                val base =
                    if (calendarAccess)
                        calendarSchedule(
                                shifts,
                                app.calendar.events(shifts.map { it.calendarEventId }),
                                ZoneId.systemDefault(),
                                extras,
                            )
                            .periods
                    else
                        shifts
                            .filter { it.type == CellType.WORK_SHIFT.name }
                            .map {
                                WorkPeriod(
                                    it.id,
                                    java.time.LocalDate.parse(it.date),
                                    java.time.LocalTime.parse(it.startTime),
                                    java.time.LocalTime.parse(it.endTime),
                                    WorkSource.SCHEDULE,
                                )
                            }
                base + extras.map { it.period() }
            }
        val now = System.currentTimeMillis()
        val reminders = workReminders(periods, settings.notificationMinutes, ZoneId.systemDefault())
        val delivered = preferences.getStringSet("delivered", emptySet()).orEmpty().toMutableSet()
        delivered.retainAll(reminders.filter { it.end > now }.map { it.key }.toSet())
        val due = reminders.firstOrNull {
            it.key == deliveryKey && it.trigger <= now && it.end > now && it.key !in delivered
        }
        if (due != null) {
            show(due, settings)
            delivered += due.key
        }
        // Limit registered alarms; periodic refresh rolls the window forward for longer schedules.
        val next =
            reminders
                .filter {
                    it.end > now && (it.trigger > now || it.key in previous) && it.key !in delivered
                }
                .take(64)
        val keys = next.map { it.key }.toSet()
        (previous - keys).forEach { alarms.cancel(pending(it)) }
        for (reminder in next) {
            val operation = pending(reminder.key)
            try {
                if (canScheduleExactly())
                    alarms.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.trigger,
                        operation,
                    )
                else
                    alarms.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.trigger,
                        operation,
                    )
            } catch (_: SecurityException) {
                // Special access may be revoked between the permission check and scheduling.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.trigger, operation)
            }
        }
        preferences
            .edit()
            .putStringSet("scheduled", keys)
            .putStringSet("delivered", delivered)
            .apply()
    }

    private fun show(reminder: WorkReminder, settings: SettingsEntity) {
        if (!canNotify()) return
        val format = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())
        val body =
            app.getString(
                R.string.notification_work_body,
                format.format(Instant.ofEpochMilli(reminder.start)),
                format.format(Instant.ofEpochMilli(reminder.end)),
            )
        val open =
            PendingIntent.getActivity(
                app,
                0,
                Intent(app, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(app, channelId(settings))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(app.getString(R.string.default_event_title))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        manager.notify(reminder.key, 1, notification)
    }
}

class ReminderRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            (applicationContext as TurniApplication).notifications.refresh()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("ShiftNotifications", "Cannot refresh work reminders", error)
            Result.retry()
        }
}

class ShiftReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TurniApplication
        val pending = goAsync()
        app.backgroundScope.launch {
            try {
                app.notifications.refresh(intent.data?.lastPathSegment)
            } catch (error: Exception) {
                Log.e("ShiftNotifications", "Cannot deliver work reminder", error)
                app.notifications.requestRefresh()
            } finally {
                pending.finish()
            }
        }
    }
}
