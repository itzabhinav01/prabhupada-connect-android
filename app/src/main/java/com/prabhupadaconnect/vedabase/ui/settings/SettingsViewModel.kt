package com.prabhupadaconnect.vedabase.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhupadaconnect.vedabase.core.model.AppSettings
import com.prabhupadaconnect.vedabase.core.model.AppTheme
import com.prabhupadaconnect.vedabase.core.model.ReadingFontSize
import com.prabhupadaconnect.vedabase.core.model.ReadingLineSpacing
import com.prabhupadaconnect.vedabase.core.model.ReadingWidthOption
import com.prabhupadaconnect.vedabase.core.model.SyncResult
import com.prabhupadaconnect.vedabase.core.model.SyncStatusInfo
import com.prabhupadaconnect.vedabase.data.settings.SettingsDataStore
import com.prabhupadaconnect.vedabase.data.sync.ResearchSyncService
import com.prabhupadaconnect.vedabase.data.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val syncStatus: SyncStatusInfo = SyncStatusInfo(isConfigured = false),
    val isSyncing: Boolean = false,
    val lastSyncResult: SyncResult? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val syncService: ResearchSyncService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val syncStatusFlow = MutableStateFlow(SyncStatusInfo(isConfigured = false))
    private val isSyncingFlow = MutableStateFlow(false)
    private val lastResultFlow = MutableStateFlow<SyncResult?>(null)

    val uiState: StateFlow<SettingsUiState> = kotlinx.coroutines.flow.combine(
        settingsDataStore.settings, syncStatusFlow, isSyncingFlow, lastResultFlow
    ) { settings, status, syncing, result -> SettingsUiState(settings, status, syncing, result) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        refreshSyncStatus()
    }

    fun refreshSyncStatus() = viewModelScope.launch {
        syncStatusFlow.value = syncService.getSyncStatus()
    }

    fun setTheme(theme: AppTheme) = viewModelScope.launch { settingsDataStore.setTheme(theme) }
    fun setFontSize(size: ReadingFontSize) = viewModelScope.launch { settingsDataStore.setFontSize(size) }
    fun setLineSpacing(spacing: ReadingLineSpacing) = viewModelScope.launch { settingsDataStore.setLineSpacing(spacing) }
    fun setReadingWidth(width: ReadingWidthOption) = viewModelScope.launch { settingsDataStore.setReadingWidth(width) }
    fun setFocusMode(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setFocusMode(enabled) }
    fun setShowTransliteration(show: Boolean) = viewModelScope.launch { settingsDataStore.setShowTransliteration(show) }
    fun setShowSynonyms(show: Boolean) = viewModelScope.launch { settingsDataStore.setShowSynonyms(show) }
    fun setShowPurport(show: Boolean) = viewModelScope.launch { settingsDataStore.setShowPurport(show) }

    fun connectSupabase(projectUrl: String, apiKey: String, email: String?, password: String?) = viewModelScope.launch {
        syncService.configureSupabase(projectUrl, apiKey, email, password)
        refreshSyncStatus()
    }

    fun disconnectSync() = viewModelScope.launch {
        syncService.disconnectProvider()
        SyncWorker.cancel(appContext)
        refreshSyncStatus()
    }

    fun syncNow() = viewModelScope.launch {
        isSyncingFlow.value = true
        val result = syncService.syncNow()
        lastResultFlow.value = result
        isSyncingFlow.value = false
        refreshSyncStatus()
    }

    fun setSyncInterval(minutes: Int) = viewModelScope.launch {
        syncService.setSyncInterval(minutes)
        SyncWorker.schedule(appContext, minutes)
        refreshSyncStatus()
    }
}
