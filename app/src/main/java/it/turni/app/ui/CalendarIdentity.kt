package it.turni.app.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import it.turni.app.R
import it.turni.app.calendar.WritableCalendar

/**
 * Resolve the viewer independently of the account's sync adapter; never label a chooser as an app.
 */
fun calendarViewer(context: Context): String? {
    val intent =
        Intent(
                Intent.ACTION_VIEW,
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, 0),
            )
            .setDataAndType(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, 0),
                "vnd.android.cursor.item/event",
            )
    return try {
        val pm = context.packageManager
        val resolved =
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
                ?: return null
        val handlers = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (
            handlers.none {
                it.activityInfo.packageName == resolved.packageName &&
                    it.activityInfo.name == resolved.name
            }
        )
            null
        else resolved.applicationInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() }
    } catch (_: SecurityException) {
        null
    }
}

@Composable
fun calendarViewerLabel(): String {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        revision++
        onPauseOrDispose {}
    }
    return remember(context, revision) { calendarViewer(context) }
        ?: stringResource(R.string.calendar_viewer_choose)
}

@Composable
fun calendarService(calendar: WritableCalendar): String =
    stringResource(
        when {
            calendar.localOnly -> R.string.calendar_service_local
            calendar.accountType == "com.google" -> R.string.calendar_service_google
            calendar.accountType in
                setOf("com.android.exchange", "com.google.android.gm.exchange") ->
                R.string.calendar_service_exchange
            else -> R.string.calendar_service_other
        }
    )

@Composable
fun calendarName(calendar: WritableCalendar): String =
    if (
        calendar.name.isBlank() ||
            (!calendar.localOnly &&
                calendar.account.isNotBlank() &&
                calendar.name.trim().equals(calendar.account.trim(), ignoreCase = true))
    )
        stringResource(R.string.calendar_primary)
    else calendar.name

@Composable
fun CalendarDestination(calendar: WritableCalendar) {
    val viewer = calendarViewerLabel()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(calendarName(calendar), style = MaterialTheme.typography.titleMedium)
        Text(
            viewer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (calendar.localOnly)
            Text(
                stringResource(R.string.calendar_service_local),
                style = MaterialTheme.typography.bodyMedium,
            )
        else if (calendar.account.isNotBlank())
            Text(calendar.account, style = MaterialTheme.typography.bodyMedium)
    }
}
