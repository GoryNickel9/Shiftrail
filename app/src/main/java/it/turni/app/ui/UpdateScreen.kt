package it.turni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.turni.app.BuildConfig
import it.turni.app.R
import it.turni.app.update.UpdateState

@Composable
fun UpdateSettings(state: UpdateState, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.updates), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.update_version, BuildConfig.VERSION_NAME))
        Text(stringResource(R.string.update_auto_note), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { vm.checkUpdates() },
            enabled = !state.checking && !state.downloading,
            modifier = Modifier.testTag("check_updates"),
        ) {
            Text(
                stringResource(
                    if (state.checking) R.string.update_checking else R.string.update_check
                )
            )
        }
        state.message?.let { Text(it, Modifier.testTag("update_message")) }
    }
}

@Composable
fun RequiredUpdateScreen(
    state: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
) {
    val release = state.release ?: return
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("required_update")
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.update_required),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(R.string.update_available, release.versionName),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(R.string.update_required_note))
            Text(
                stringResource(
                    if (state.ready) R.string.update_install_note else R.string.update_download_note
                )
            )
            if (state.downloading || state.checking) CircularProgressIndicator()
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.message != null)
                OutlinedButton(onClick = onRetry, enabled = !state.downloading && !state.checking) {
                    Text(stringResource(R.string.update_check))
                }
        }
        ActionFooter(
            stringResource(if (state.ready) R.string.update_install else R.string.update_download),
            enabled = !state.downloading && !state.checking,
            action = if (state.ready) onInstall else onDownload,
        )
    }
}
