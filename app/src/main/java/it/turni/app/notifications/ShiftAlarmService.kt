package it.turni.app.notifications

import android.app.*
import android.content.*
import android.media.*
import android.net.Uri
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import it.turni.app.R
import it.turni.app.TurniApplication
import it.turni.core.WorkReminder
import it.turni.core.workTimeRange
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveShiftAlarm(val key: String, val start: Long, val end: Long)

/** Shared lifetime of the ringing service and its optional lock-screen activity. */
object ShiftAlarmState {
    internal val mutable = MutableStateFlow<ActiveShiftAlarm?>(null)
    val active = mutable.asStateFlow()
}

class ShiftAlarmService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var audioFocus: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopSelf() }
    private val vibrator by lazy { getSystemService(Vibrator::class.java) }
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private var current: ActiveShiftAlarm? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val key = intent?.getStringExtra(KEY)
        if (intent?.action == STOP) {
            if (key == current?.key || current == null) stopSelf()
            return START_NOT_STICKY
        }
        val start = intent?.getLongExtra(START, 0) ?: 0
        val end = intent?.getLongExtra(END, 0) ?: 0
        if (key.isNullOrBlank() || start <= 0 || end <= System.currentTimeMillis()) {
            if (current == null) stopSelf()
            return START_NOT_STICKY
        }
        if (current?.key == key) return START_NOT_STICKY
        val app = application as TurniApplication
        if (
            !app.notifications.canNotify() ||
                ensureChannel(this).importance == NotificationManager.IMPORTANCE_NONE
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        stopSignals()
        val alarm = ActiveShiftAlarm(key, start, end)
        current = alarm
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, ShiftAlarmActivity::class.java)
                    .setData(Uri.parse("shiftrail://alarm/$key")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                this,
                0,
                Intent(this, ShiftAlarmService::class.java)
                    .setAction(STOP)
                    .setData(Uri.parse("shiftrail://alarm-stop/$key"))
                    .putExtra(KEY, key),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(workTimeRange(start, end))
                .setShowWhen(false)
                .setContentIntent(open)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setOngoing(true)
                .setTimeoutAfter(TIMEOUT_MS)
                .addAction(0, getString(R.string.stop_alarm), stop)
        if (app.notifications.canUseFullScreenAlarm()) builder.setFullScreenIntent(open, true)
        startForeground(NOTIFICATION_ID, builder.build())
        ShiftAlarmState.mutable.value = alarm
        // Recheck saved consent after the asynchronous delivery-to-service handoff.
        serviceScope.launch {
            val saved = withContext(Dispatchers.IO) { app.database.dao().settings() }
            if (
                saved?.notificationsEnabled != true ||
                    !saved.fullScreenAlarm ||
                    key !=
                        "$start-${it.turni.core.normalizeNotificationMinutes(saved.notificationMinutes)}"
            ) {
                if (current?.key == key) stopSelf()
                return@launch
            }
            if (current?.key != key) return@launch
            if (saved.notificationSound) startSound()
            if (saved.notificationVibration)
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        LongArray((TIMEOUT_MS / 500).toInt() + 1) { if (it == 0) 0L else 500L },
                        -1,
                    ),
                    alarmAudio(),
                )
        }
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, TIMEOUT_MS)
        return START_NOT_STICKY
    }

    private fun startSound() {
        val focus =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(alarmAudio())
                .setOnAudioFocusChangeListener { change ->
                    if (
                        change == AudioManager.AUDIOFOCUS_LOSS ||
                            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    )
                        stopSelf()
                }
                .build()
        audioFocus = focus
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        try {
            val uri =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val next = MediaPlayer()
            player = next
            next.setAudioAttributes(alarmAudio())
            next.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            next.setDataSource(this, uri)
            next.isLooping = true
            next.setOnPreparedListener { if (player === it) it.start() }
            next.setOnErrorListener { _, what, extra ->
                Log.e("ShiftAlarm", "Alarm audio failed: $what/$extra")
                stopAudio()
                true
            }
            next.prepareAsync()
        } catch (error: Exception) {
            Log.e("ShiftAlarm", "Cannot play alarm", error)
            stopAudio()
        }
    }

    private fun stopSignals() {
        stopAudio()
        vibrator.cancel()
    }

    private fun stopAudio() {
        player?.release()
        player = null
        audioFocus?.let { audio.abandonAudioFocusRequest(it) }
        audioFocus = null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        handler.removeCallbacks(timeout)
        stopSignals()
        if (ShiftAlarmState.active.value?.key == current?.key) ShiftAlarmState.mutable.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "shift-full-screen-alarm"
        const val NOTIFICATION_ID = 4242
        const val STOP = "it.turni.app.STOP_SHIFT_ALARM"
        const val KEY = "key"
        const val START = "start"
        const val END = "end"
        private const val TIMEOUT_MS = 120_000L

        private fun alarmAudio() =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

        fun ensureChannel(context: Context): NotificationChannel {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel =
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.alarm_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                )
            // The service owns looping audio and vibration, so channel sound must not ring twice.
            channel.setSound(null, null)
            channel.enableVibration(false)
            manager.createNotificationChannel(channel)
            return manager.getNotificationChannel(CHANNEL)
        }

        // stopService resolves the Intent component, not listener object identity.
        @android.annotation.SuppressLint("ImplicitSamInstance")
        fun stop(context: Context) {
            context.stopService(Intent(context, ShiftAlarmService::class.java))
        }

        fun start(context: Context, reminder: WorkReminder) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ShiftAlarmService::class.java)
                    .putExtra(KEY, reminder.key)
                    .putExtra(START, reminder.start)
                    .putExtra(END, reminder.end),
            )
        }
    }
}
