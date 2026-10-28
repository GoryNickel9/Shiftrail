package it.turni.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import it.turni.app.R
import it.turni.app.TurniApplication
import it.turni.app.data.SettingsEntity

@Composable
fun NotificationSettings(
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    fullScreenAlarm: Boolean,
    onFullScreenAlarm: (Boolean) -> Unit,
    sound: Boolean,
    onSound: (Boolean) -> Unit,
    vibration: Boolean,
    onVibration: (Boolean) -> Unit,
    hours: String,
    onHours: (String) -> Unit,
    minutes: String,
    onMinutes: (String) -> Unit,
    validTime: Boolean,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val notifications = (context.applicationContext as TurniApplication).notifications
    var revision by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        revision++
        onPauseOrDispose {}
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            revision++
        }
    val canNotify = remember(revision) { notifications.canNotify() }
    val exact = remember(revision) { notifications.canScheduleExactly() }
    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            onError(context.getString(R.string.notification_settings_unavailable))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NotificationToggle(
            stringResource(R.string.notifications_enable),
            enabled,
            "notifications_enabled",
        ) {
            onEnabled(it)
            if (
                it &&
                    Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
            )
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (enabled) {
            Text(
                stringResource(R.string.notification_advance),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                NumberWheel(
                    hours.toIntOrNull() ?: 0,
                    (0..24).toList(),
                    stringResource(R.string.notification_hours),
                    "notification_hours",
                ) {
                    onHours(it.toString())
                }
                NumberWheel(
                    minutes.toIntOrNull() ?: 0,
                    (0..55 step 5).toList(),
                    stringResource(R.string.notification_minutes),
                    "notification_minutes",
                ) {
                    onMinutes(it.toString())
                }
            }
            if (!validTime)
                Text(
                    stringResource(R.string.notification_time_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            NotificationToggle(
                stringResource(R.string.full_screen_alarm),
                fullScreenAlarm,
                "full_screen_alarm",
                onFullScreenAlarm,
            )
            if (fullScreenAlarm) {
                if (!notifications.canUseFullScreenAlarm()) {
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 34)
                                open(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        Uri.parse("package:${context.packageName}"),
                                    )
                                )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("allow_full_screen_alarm"),
                    ) {
                        Text(stringResource(R.string.allow_full_screen_alarm))
                    }
                }
            }
            NotificationToggle(
                stringResource(R.string.notification_sound),
                sound,
                "notification_sound",
                onSound,
            )
            NotificationToggle(
                stringResource(R.string.notification_vibration),
                vibration,
                "notification_vibration",
                onVibration,
            )
            val channel =
                if (fullScreenAlarm) notifications.ensureAlarmChannel()
                else
                    notifications.ensureChannel(
                        SettingsEntity(notificationSound = sound, notificationVibration = vibration)
                    )
            if (!canNotify || channel.importance == NotificationManager.IMPORTANCE_NONE)
                Text(
                    stringResource(R.string.notifications_blocked),
                    color = MaterialTheme.colorScheme.error,
                )
            OutlinedButton(
                onClick = {
                    open(
                        Intent(
                                if (canNotify) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                                else Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            )
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, channel.id)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.notification_system_settings))
            }
            if (!exact) {
                Text(
                    stringResource(R.string.notifications_inexact),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (Build.VERSION.SDK_INT >= 31)
                    OutlinedButton(
                        onClick = {
                            open(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.notification_allow_exact))
                    }
            }
        }
    }
}

@Composable
private fun NotificationToggle(
    label: String,
    checked: Boolean,
    tag: String,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChanged, Modifier.testTag(tag).semantics { contentDescription = label })
    }
}
