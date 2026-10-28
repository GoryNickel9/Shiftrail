package it.turni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.turni.app.BuildConfig
import it.turni.app.R
import it.turni.app.update.UpdateState

@Composable
fun UpdateSettings(state: UpdateState, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.update_version, BuildConfig.VERSION_NAME))
        val betaLabel = stringResource(R.string.beta_join)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(betaLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(
                state.betaEnabled,
                vm::setBetaParticipation,
                Modifier.testTag("beta_enabled").semantics { contentDescription = betaLabel },
                enabled = !state.downloading,
            )
        }
        if (BuildConfig.VERSION_NAME.contains("-beta."))
            Text(
                stringResource(R.string.beta_installed),
                style = MaterialTheme.typography.bodySmall,
            )
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
        state.betaRelease
            ?.takeIf { state.betaEnabled }
            ?.let { beta ->
                OutlinedCard(Modifier.fillMaxWidth().testTag("beta_available")) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.beta_available, beta.versionName),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.beta_optional),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (state.downloading) CircularProgressIndicator()
                        Button(
                            onClick =
                                if (state.ready && state.downloadedRelease == beta)
                                    vm::installUpdate
                                else vm::downloadBetaUpdate,
                            enabled = !state.checking && !state.downloading,
                        ) {
                            Text(
                                stringResource(
                                    if (state.ready && state.downloadedRelease == beta)
                                        R.string.update_install
                                    else R.string.beta_download
                                )
                            )
                        }
                    }
                }
            }
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
    val ready = state.ready && state.downloadedRelease == release
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
                    if (ready) R.string.update_install_note else R.string.update_download_note
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
            stringResource(if (ready) R.string.update_install else R.string.update_download),
            enabled = !state.downloading && !state.checking,
            action = if (ready) onInstall else onDownload,
        )
    }
}
