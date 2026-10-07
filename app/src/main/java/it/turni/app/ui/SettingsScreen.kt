package it.turni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.turni.app.R

@Composable
fun SettingsScreen(state: AppState, vm: AppViewModel, onSaved: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(state.profile?.name.orEmpty()) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                name,
                {
                    name = it
                    invalid = false
                },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = invalid,
            )
        }
        ActionFooter(
            stringResource(R.string.save),
            enabled = !state.busy,
            note = if (invalid) stringResource(R.string.invalid_name) else null,
            action = {
                invalid = name.isBlank() || name.trim().length > 160
                if (!invalid) vm.saveName(name, onSaved)
            },
        )
    }
}
