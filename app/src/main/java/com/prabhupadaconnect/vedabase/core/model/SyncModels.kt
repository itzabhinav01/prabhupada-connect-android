package com.prabhupadaconnect.vedabase.core.model

import java.time.Instant

enum class SyncErrorCategory { None, Authentication, Permission, Network, RateLimited, ServerError, Canceled, Other }

enum class SyncState {
    NotConfigured,
    ConfiguredNeverSynced,
    Syncing,
    Synced,
    LocalChangesPending,
    Offline,
    AuthenticationFailure,
    PermissionFailure,
    NetworkFailure,
    SyncFailed
}

data class SyncStatusInfo(
    val isConfigured: Boolean,
    val providerType: String = "None",
    val lastSyncUtc: Instant? = null,
    val lastSyncResult: String = "",
    val lastSyncMessage: String = "",
    val uploadedCount: Int = 0,
    val downloadedCount: Int = 0,
    val conflictCount: Int = 0,
    val isSyncing: Boolean = false,
    val projectUrl: String? = null,
    val userEmail: String? = null,
    val syncIntervalMinutes: Int = 0,
    val pendingLocalChanges: Int = 0,
    val lastErrorCategory: SyncErrorCategory = SyncErrorCategory.None
) {
    val isPeriodicSyncEnabled: Boolean get() = syncIntervalMinutes > 0

    val state: SyncState
        get() {
            if (isSyncing) return SyncState.Syncing
            if (!isConfigured) return SyncState.NotConfigured
            if (lastSyncUtc == null) return SyncState.ConfiguredNeverSynced

            if (!lastSyncResult.equals("Success", ignoreCase = true) &&
                !lastSyncResult.equals("Disconnected", ignoreCase = true)
            ) {
                return when (lastErrorCategory) {
                    SyncErrorCategory.Authentication -> SyncState.AuthenticationFailure
                    SyncErrorCategory.Permission -> SyncState.PermissionFailure
                    SyncErrorCategory.Network, SyncErrorCategory.RateLimited, SyncErrorCategory.ServerError -> SyncState.NetworkFailure
                    else -> SyncState.SyncFailed
                }
            }

            return if (pendingLocalChanges > 0) SyncState.LocalChangesPending else SyncState.Synced
        }
}

data class SyncResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val uploadedCount: Int = 0,
    val downloadedCount: Int = 0,
    val conflictCount: Int = 0,
    val snapshotPath: String? = null
)

data class SyncConnectionTestResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val tablesExist: Boolean = false,
    val missingTables: List<String> = emptyList()
)

data class SyncPreviewResult(
    val localChangesCount: Int = 0,
    val remoteChangesCount: Int = 0,
    val errorMessage: String? = null
) {
    val canSync: Boolean get() = errorMessage.isNullOrEmpty()
}

data class SupabaseConfig(
    val projectUrl: String,
    val anonKey: String,
    var userEmail: String? = null,
    var userPassword: String? = null,
    var authToken: String? = null,
    var refreshToken: String? = null,
    var userId: String? = null
)
