package com.prabhupadaconnect.vedabase.data.sync

import com.prabhupadaconnect.vedabase.core.model.LocalChangeSet
import com.prabhupadaconnect.vedabase.core.model.RemoteChangeSet
import com.prabhupadaconnect.vedabase.core.model.SyncConnectionTestResult
import java.time.Instant

/** Thrown for any non-2xx HTTP response from a sync provider - carries the status code so [ResearchSyncService] can apply bounded, specific recovery (401 re-auth, 429/5xx backoff-and-retry-once). */
class SyncHttpException(val statusCode: Int, message: String) : Exception(message)

interface SyncProvider {
    val providerId: String
    suspend fun authenticate(): Boolean
    suspend fun testConnection(): SyncConnectionTestResult
    suspend fun pullChanges(sinceUtc: Instant?): RemoteChangeSet
    suspend fun pushChanges(changes: LocalChangeSet): Int
    suspend fun disconnect()
}
