package com.prabhupadaconnect.vedabase.data.sync

import com.prabhupadaconnect.vedabase.core.model.SupabaseConfig
import com.prabhupadaconnect.vedabase.core.model.SyncConnectionTestResult
import com.prabhupadaconnect.vedabase.core.model.SyncErrorCategory
import com.prabhupadaconnect.vedabase.core.model.SyncPreviewResult
import com.prabhupadaconnect.vedabase.core.model.SyncResult
import com.prabhupadaconnect.vedabase.core.model.SyncStatusInfo
import com.prabhupadaconnect.vedabase.core.util.IsoTime
import com.prabhupadaconnect.vedabase.data.settings.CredentialStorage
import com.prabhupadaconnect.vedabase.data.user.UserRepository
import io.ktor.client.HttpClient
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates one sync cycle end-to-end (safety snapshot -> pull -> LWW
 * merge -> enumerate local changes -> push -> advance checkpoint) and
 * exposes live status for the Settings screen. Ported 1:1 from the desktop
 * app's `ResearchSyncService` (C#): same bounded, specific HTTP recovery
 * (401 -> one re-authenticate-and-retry, 429/5xx -> one fixed-delay retry,
 * anything else -> no retry), same rule that a failed sync never advances
 * the checkpoint.
 */
@Singleton
class ResearchSyncService @Inject constructor(
    private val userRepository: UserRepository,
    private val backupService: UserDataBackupService,
    private val credentialStorage: CredentialStorage,
    private val httpClient: HttpClient
) {
    private var currentProvider: SyncProvider? = null
    private val syncLock = Mutex()
    @Volatile private var isSyncing = false

    private suspend fun <T> executeWithRecovery(operation: suspend () -> T): T {
        return try {
            operation()
        } catch (ex: SyncHttpException) {
            when {
                ex.statusCode == 401 && currentProvider != null -> {
                    val reauthed = currentProvider!!.authenticate()
                    if (!reauthed) throw ex
                    operation()
                }
                ex.statusCode == 429 || ex.statusCode >= 500 -> {
                    delay(2000)
                    operation()
                }
                else -> throw ex
            }
        }
    }

    private fun categorizeError(ex: Throwable): SyncErrorCategory = when {
        ex is CancellationException -> SyncErrorCategory.Canceled
        ex is SyncHttpException -> when (ex.statusCode) {
            401 -> SyncErrorCategory.Authentication
            403 -> SyncErrorCategory.Permission
            429 -> SyncErrorCategory.RateLimited
            500 -> SyncErrorCategory.ServerError
            in 500..599 -> SyncErrorCategory.ServerError
            else -> SyncErrorCategory.Other
        }
        else -> SyncErrorCategory.Other
    }

    private suspend fun ensureProviderInitialized() {
        if (currentProvider != null) return

        val providerType = userRepository.getSyncMetadata("SyncProviderType")
        if (!providerType.equals("Supabase", ignoreCase = true)) return

        val url = userRepository.getSyncMetadata("SupabaseProjectUrl")
        val email = userRepository.getSyncMetadata("SupabaseUserEmail")
        val apiKey = credentialStorage.getCredential("Supabase_ApiKey")
        val password = credentialStorage.getCredential("Supabase_Password")
        val authToken = credentialStorage.getCredential("Supabase_AuthToken")
        val userId = credentialStorage.getCredential("Supabase_UserId")
        val deviceId = userRepository.getDeviceId()

        if (!url.isNullOrBlank() && !apiKey.isNullOrBlank()) {
            val config = SupabaseConfig(
                projectUrl = url, anonKey = apiKey, userEmail = email, userPassword = password,
                authToken = authToken, userId = userId
            )
            currentProvider = SupabaseSyncProvider(httpClient, config, deviceId)
        }
    }

    suspend fun getSyncStatus(): SyncStatusInfo {
        ensureProviderInitialized()

        val providerType = userRepository.getSyncMetadata("SyncProviderType") ?: "None"
        val lastSyncStr = userRepository.getSyncMetadata("LastSyncUtc")
        val lastResult = userRepository.getSyncMetadata("LastSyncResult") ?: ""
        val lastMessage = userRepository.getSyncMetadata("LastSyncMessage") ?: ""
        val projectUrl = userRepository.getSyncMetadata("SupabaseProjectUrl")
        val userEmail = userRepository.getSyncMetadata("SupabaseUserEmail")

        val uploaded = userRepository.getSyncMetadata("LastSyncUploaded")?.toIntOrNull() ?: 0
        val downloaded = userRepository.getSyncMetadata("LastSyncDownloaded")?.toIntOrNull() ?: 0
        val conflicts = userRepository.getSyncMetadata("LastSyncConflicts")?.toIntOrNull() ?: 0
        val intervalMinutes = userRepository.getSyncMetadata("SyncIntervalMinutes")?.toIntOrNull() ?: 0
        val errorCategory = runCatching {
            SyncErrorCategory.valueOf(userRepository.getSyncMetadata("LastSyncErrorCategory") ?: "None")
        }.getOrDefault(SyncErrorCategory.None)

        val lastSyncUtc = lastSyncStr?.takeIf { it.isNotBlank() }?.let { runCatching { IsoTime.parse(it) }.getOrNull() }

        val pendingLocal = runCatching { userRepository.getLocalChanges(lastSyncUtc).totalCount }.getOrDefault(0)

        return SyncStatusInfo(
            isConfigured = currentProvider != null || (providerType != "None" && !projectUrl.isNullOrBlank()),
            providerType = providerType,
            lastSyncUtc = lastSyncUtc,
            lastSyncResult = lastResult,
            lastSyncMessage = lastMessage,
            uploadedCount = uploaded,
            downloadedCount = downloaded,
            conflictCount = conflicts,
            isSyncing = isSyncing,
            projectUrl = projectUrl,
            userEmail = userEmail,
            syncIntervalMinutes = intervalMinutes,
            pendingLocalChanges = pendingLocal,
            lastErrorCategory = errorCategory
        )
    }

    suspend fun configureSupabase(
        projectUrl: String,
        apiKey: String,
        userEmail: String? = null,
        userPassword: String? = null
    ): SyncConnectionTestResult {
        if (projectUrl.isBlank() || apiKey.isBlank()) {
            return SyncConnectionTestResult(success = false, errorMessage = "Project URL and API Key are required.")
        }

        val deviceId = userRepository.getDeviceId()
        val testConfig = SupabaseConfig(projectUrl = projectUrl, anonKey = apiKey, userEmail = userEmail, userPassword = userPassword)
        val testProvider = SupabaseSyncProvider(httpClient, testConfig, deviceId)
        val result = testProvider.testConnection()

        if (result.success) {
            val prevUrl = userRepository.getSyncMetadata("SupabaseProjectUrl")
            val prevEmail = userRepository.getSyncMetadata("SupabaseUserEmail")
            if (!prevUrl.equals(projectUrl, ignoreCase = true) || !prevEmail.equals(userEmail, ignoreCase = true)) {
                userRepository.setSyncMetadata("LastSyncUtc", "")
            }

            credentialStorage.saveCredential("Supabase_ApiKey", apiKey)
            if (!userPassword.isNullOrEmpty()) {
                credentialStorage.saveCredential("Supabase_Password", userPassword)
            } else {
                credentialStorage.deleteCredential("Supabase_Password")
            }
            testConfig.authToken?.let { credentialStorage.saveCredential("Supabase_AuthToken", it) }
            testConfig.userId?.let { credentialStorage.saveCredential("Supabase_UserId", it) }

            userRepository.setSyncMetadata("SupabaseProjectUrl", projectUrl)
            userRepository.setSyncMetadata("SupabaseUserEmail", userEmail ?: "")
            userRepository.setSyncMetadata("SyncProviderType", "Supabase")
            currentProvider = testProvider
        }

        return result
    }

    suspend fun disconnectProvider() {
        currentProvider?.disconnect()
        currentProvider = null

        credentialStorage.deleteCredential("Supabase_ApiKey")
        credentialStorage.deleteCredential("Supabase_Password")
        credentialStorage.deleteCredential("Supabase_AuthToken")
        credentialStorage.deleteCredential("Supabase_UserId")

        userRepository.setSyncMetadata("SyncProviderType", "None")
        userRepository.setSyncMetadata("SupabaseProjectUrl", "")
        userRepository.setSyncMetadata("SupabaseUserEmail", "")
        userRepository.setSyncMetadata("LastSyncResult", "Disconnected")
        userRepository.setSyncMetadata("LastSyncMessage", "Cloud sync disconnected. Local database preserved intact.")
    }

    suspend fun previewSync(): SyncPreviewResult {
        ensureProviderInitialized()
        val provider = currentProvider ?: return SyncPreviewResult(errorMessage = "No cloud provider is currently connected.")

        return try {
            val lastSyncUtc = userRepository.getSyncMetadata("LastSyncUtc")?.takeIf { it.isNotBlank() }?.let(IsoTime::parse)
            val localChanges = userRepository.getLocalChanges(lastSyncUtc)
            val remoteChanges = provider.pullChanges(lastSyncUtc)
            SyncPreviewResult(localChangesCount = localChanges.totalCount, remoteChangesCount = remoteChanges.totalCount)
        } catch (ex: Exception) {
            SyncPreviewResult(errorMessage = "Preview failed: ${ex.message}")
        }
    }

    suspend fun syncNow(): SyncResult {
        ensureProviderInitialized()
        val provider = currentProvider
            ?: return SyncResult(success = false, errorMessage = "No sync provider configured. Connect a Supabase project first.")

        if (!syncLock.tryLock()) {
            return SyncResult(success = false, errorMessage = "A synchronization operation is already in progress.")
        }

        isSyncing = true
        var snapshotPath = ""

        try {
            runCatching { snapshotPath = backupService.createLocalSnapshot() }

            val lastSyncUtc = userRepository.getSyncMetadata("LastSyncUtc")?.takeIf { it.isNotBlank() }?.let(IsoTime::parse)
            val syncStart = Instant.now()
            userRepository.setSyncMetadata("LastAttemptedSyncUtc", IsoTime.format(syncStart))

            val remoteChanges = executeWithRecovery { provider.pullChanges(lastSyncUtc) }

            val deviceId = userRepository.getDeviceId()
            val conflicts = userRepository.mergeSyncChanges(remoteChanges, deviceId)

            val localChanges = userRepository.getLocalChanges(lastSyncUtc)

            val pushed = executeWithRecovery { provider.pushChanges(localChanges) }

            val durationSeconds = (Instant.now().toEpochMilli() - syncStart.toEpochMilli()) / 1000.0
            userRepository.setSyncMetadata("LastSyncUtc", IsoTime.format(syncStart))
            userRepository.setSyncMetadata("LastSyncResult", "Success")
            userRepository.setSyncMetadata("LastSyncErrorCategory", SyncErrorCategory.None.name)
            userRepository.setSyncMetadata("LastSyncUploaded", pushed.toString())
            userRepository.setSyncMetadata("LastSyncDownloaded", remoteChanges.totalCount.toString())
            userRepository.setSyncMetadata("LastSyncConflicts", conflicts.toString())
            userRepository.setSyncMetadata("LastSyncDurationSeconds", "%.2f".format(durationSeconds))
            userRepository.setSyncMetadata(
                "LastSyncMessage",
                "Synced: $pushed uploaded, ${remoteChanges.totalCount} downloaded, $conflicts conflicts resolved."
            )

            return SyncResult(
                success = true, uploadedCount = pushed, downloadedCount = remoteChanges.totalCount,
                conflictCount = conflicts, snapshotPath = snapshotPath
            )
        } catch (ex: CancellationException) {
            // Checkpoint deliberately left untouched - an interrupted sync
            // must never be mistaken for a completed one.
            userRepository.setSyncMetadata("LastSyncResult", "Canceled")
            userRepository.setSyncMetadata("LastSyncErrorCategory", SyncErrorCategory.Canceled.name)
            userRepository.setSyncMetadata("LastSyncMessage", "Synchronization operation was canceled.")
            return SyncResult(success = false, errorMessage = "Synchronization operation was canceled.", snapshotPath = snapshotPath)
        } catch (ex: Exception) {
            val category = categorizeError(ex)
            userRepository.setSyncMetadata("LastSyncResult", "Error")
            userRepository.setSyncMetadata("LastSyncErrorCategory", category.name)
            userRepository.setSyncMetadata("LastSyncMessage", "Sync failed: ${ex.message}")
            return SyncResult(success = false, errorMessage = ex.message, snapshotPath = snapshotPath)
        } finally {
            isSyncing = false
            syncLock.unlock()
        }
    }

    suspend fun setSyncInterval(intervalMinutes: Int) {
        userRepository.setSyncMetadata("SyncIntervalMinutes", intervalMinutes.toString())
    }
}
