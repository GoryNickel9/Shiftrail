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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    hours,
                    { if (it.length <= 3 && it.all(Char::isDigit)) onHours(it) },
                    Modifier.weight(1f).testTag("notification_hours"),
                    label = { Text(stringResource(R.string.notification_hours)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !validTime,
                )
                OutlinedTextField(
                    minutes,
                    { if (it.length <= 2 && it.all(Char::isDigit)) onMinutes(it) },
                    Modifier.weight(1f).testTag("notification_minutes"),
                    label = { Text(stringResource(R.string.notification_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !validTime,
                )
            }
            if (!validTime)
                Text(
                    stringResource(R.string.notification_time_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
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
