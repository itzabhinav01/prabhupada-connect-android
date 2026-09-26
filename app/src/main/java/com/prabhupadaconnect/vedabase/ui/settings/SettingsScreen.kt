package com.prabhupadaconnect.vedabase.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prabhupadaconnect.vedabase.core.model.AppTheme
import com.prabhupadaconnect.vedabase.core.model.ReadingFontSize
import com.prabhupadaconnect.vedabase.core.model.ReadingLineSpacing
import com.prabhupadaconnect.vedabase.core.model.ReadingWidthOption
import com.prabhupadaconnect.vedabase.core.model.SyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionTitle("Appearance")
            Text("Theme", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            Row {
                AppTheme.entries.forEach { theme ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = state.settings.theme == theme, onClick = { viewModel.setTheme(theme) })
                        Text(theme.name)
                    }
                }
            }

            Spacer()
            Text("Font size", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            Row { ReadingFontSize.entries.forEach { size ->
                FilterChip(selected = state.settings.fontSize == size, onClick = { viewModel.setFontSize(size) }, label = { Text(size.name) }, modifier = Modifier.padding(end = 8.dp))
            } }

            Spacer()
            Text("Line spacing", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            Row { ReadingLineSpacing.entries.forEach { spacing ->
                FilterChip(selected = state.settings.lineSpacing == spacing, onClick = { viewModel.setLineSpacing(spacing) }, label = { Text(spacing.name) }, modifier = Modifier.padding(end = 8.dp))
            } }

            Spacer()
            Text("Reading width", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            Row { ReadingWidthOption.entries.forEach { width ->
                FilterChip(selected = state.settings.readingWidth == width, onClick = { viewModel.setReadingWidth(width) }, label = { Text(width.name) }, modifier = Modifier.padding(end = 8.dp))
            } }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionTitle("Reading blocks")
            ToggleRow("Show transliteration", state.settings.showTransliteration, viewModel::setShowTransliteration)
            ToggleRow("Show word-for-word synonyms", state.settings.showSynonyms, viewModel::setShowSynonyms)
            ToggleRow("Show purport", state.settings.showPurport, viewModel::setShowPurport)
            ToggleRow("Focus mode", state.settings.focusModeEnabled, viewModel::setFocusMode)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionTitle("Cloud Sync (Supabase)")
            CloudSyncSection(viewModel, state)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Spacer() = androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CloudSyncSection(viewModel: SettingsViewModel, state: SettingsUiState) {
    var projectUrl by remember { mutableStateOf(state.syncStatus.projectUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(state.syncStatus.userEmail.orEmpty()) }
    var password by remember { mutableStateOf("") }

    Text("Status: ${state.syncStatus.state.readable()}")
    if (state.syncStatus.lastSyncMessage.isNotBlank()) {
        Text(state.syncStatus.lastSyncMessage, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
    }

    if (!state.syncStatus.isConfigured) {
        OutlinedTextField(value = projectUrl, onValueChange = { projectUrl = it }, label = { Text("Supabase project URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("Anon/public API key") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email (optional, for RLS auth)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password (optional)") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.connectSupabase(projectUrl.trim(), apiKey.trim(), email.ifBlank { null }, password.ifBlank { null }) },
            modifier = Modifier.padding(top = 8.dp)
        ) { Text("Connect") }
    } else {
        Row {
            Button(onClick = { viewModel.syncNow() }, enabled = !state.isSyncing) {
                Text(if (state.isSyncing) "Syncing…" else "Sync now")
            }
            OutlinedButton(onClick = { viewModel.disconnectSync() }, modifier = Modifier.padding(start = 8.dp)) { Text("Disconnect") }
        }

        Spacer()
        Text("Background sync interval", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
        Row {
            listOf(0 to "Off", 15 to "15m", 30 to "30m", 60 to "1h", 360 to "6h", 1440 to "Daily").forEach { (minutes, label) ->
                FilterChip(
                    selected = state.syncStatus.syncIntervalMinutes == minutes,
                    onClick = { viewModel.setSyncInterval(minutes) },
                    label = { Text(label) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

private fun SyncState.readable(): String = when (this) {
    SyncState.NotConfigured -> "Not connected"
    SyncState.ConfiguredNeverSynced -> "Connected, not yet synced"
    SyncState.Syncing -> "Syncing…"
    SyncState.Synced -> "Synced"
    SyncState.LocalChangesPending -> "Local changes pending"
    SyncState.Offline -> "Offline"
    SyncState.AuthenticationFailure -> "Authentication failed"
    SyncState.PermissionFailure -> "Permission denied"
    SyncState.NetworkFailure -> "Network error"
    SyncState.SyncFailed -> "Sync failed"
}
