package it.turni.app.notifications

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.turni.app.R
import it.turni.app.ui.AppAppearance
import it.turni.app.ui.ShiftrailTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ShiftAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val alarm by ShiftAlarmState.active.collectAsStateWithLifecycle()
            LaunchedEffect(alarm) { if (alarm == null) finish() }
            val dark =
                AppAppearance(this)
                    .theme()
                    .isDark(androidx.compose.foundation.isSystemInDarkTheme())
            ShiftrailTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    alarm?.let { current ->
                        val date =
                            Instant.ofEpochMilli(current.start).atZone(ZoneId.systemDefault())
                        Column(
                            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                Column(
                                    Modifier.fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .heightIn(min = maxHeight),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement =
                                        Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                                ) {
                                    Text(
                                        stringResource(R.string.alarm_title),
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                    Text(
                                        stringResource(R.string.alarm_start_label),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        date.format(DateTimeFormatter.ofPattern("HH:mm")),
                                        style = MaterialTheme.typography.displayLarge,
                                    )
                                    Text(
                                        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { ShiftAlarmService.stop(this@ShiftAlarmActivity) },
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .heightIn(min = 64.dp)
                                        .testTag("stop_shift_alarm"),
                            ) {
                                Text(stringResource(R.string.stop_alarm))
                            }
                        }
                    }
                }
            }
            // Back closes the screen; stopping the alarm remains an explicit action.
            BackHandler { finish() }
        }
    }
}
