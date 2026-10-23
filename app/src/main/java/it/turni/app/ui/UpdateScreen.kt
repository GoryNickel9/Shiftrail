package it.turni.app.ui

import androidx.compose.foundation.layout.*
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
        state.release?.let {
            Button(onClick = vm::showUpdate) {
                Text(stringResource(R.string.update_available, it.versionName))
            }
        }
        state.message?.let { Text(it, Modifier.testTag("update_message")) }
    }
}

@Composable
fun UpdateDialog(state: UpdateState, vm: AppViewModel) {
    val release = state.release ?: return
    AlertDialog(
        onDismissRequest = { if (!state.downloading) vm.dismissUpdate() },
        title = { Text(stringResource(R.string.update_available, release.versionName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        if (state.ready) R.string.update_install_note
                        else R.string.update_download_note
                    )
                )
                if (state.downloading) CircularProgressIndicator()
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (state.ready) vm.installUpdate() else vm.downloadUpdate() },
                enabled = !state.downloading,
            ) {
                Text(
                    stringResource(
                        if (state.ready) R.string.update_install else R.string.update_download
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = vm::dismissUpdate, enabled = !state.downloading) {
                Text(stringResource(R.string.update_later))
            }
        },
    )
}
