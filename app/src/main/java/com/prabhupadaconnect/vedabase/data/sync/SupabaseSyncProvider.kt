package com.prabhupadaconnect.vedabase.data.sync

import com.prabhupadaconnect.vedabase.core.model.BookmarkCollection
import com.prabhupadaconnect.vedabase.core.model.Highlight
import com.prabhupadaconnect.vedabase.core.model.HighlightColor
import com.prabhupadaconnect.vedabase.core.model.LocalChangeSet
import com.prabhupadaconnect.vedabase.core.model.RemoteChangeSet
import com.prabhupadaconnect.vedabase.core.model.SupabaseConfig
import com.prabhupadaconnect.vedabase.core.model.SyncConnectionTestResult
import com.prabhupadaconnect.vedabase.core.model.UserBookmark
import com.prabhupadaconnect.vedabase.core.model.UserNote
import com.prabhupadaconnect.vedabase.core.util.IsoTime
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Sync provider for a user-owned Supabase project via PostgREST, over
 * plain Ktor HTTP - no vendor SDK. Ported 1:1 from the desktop app's
 * `SupabaseSyncProvider` (C#): same header shape, same 24-hour pull
 * lookback buffer (a pulled record's `updated_at` only defaults on INSERT,
 * so a record created before this device's checkpoint but pushed after it
 * would otherwise never satisfy a strict `updated_at > checkpoint` filter
 * again - re-pulling a small already-seen window is harmless because
 * [com.prabhupadaconnect.vedabase.data.user.UserRepository.mergeSyncChanges]
 * is idempotent), same `Prefer: resolution=merge-duplicates` upsert.
 */
class SupabaseSyncProvider(
    private val httpClient: HttpClient,
    private val config: SupabaseConfig,
    private val deviceId: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SyncProvider {

    override val providerId: String = "Supabase"

    private val baseUrl: String
    private val authUrl: String
    private var authToken: String? = config.authToken
    private var userId: String? = config.userId

    init {
        var rootUrl = config.projectUrl.trim().trimEnd('/')
        if (rootUrl.endsWith("/rest/v1", ignoreCase = true)) rootUrl = rootUrl.removeSuffix("/rest/v1").trimEnd('/')
        baseUrl = "$rootUrl/rest/v1"
        authUrl = "$rootUrl/auth/v1"
    }

    private fun bearerToken(): String = authToken ?: config.authToken ?: config.anonKey

    private suspend fun HttpResponse.throwIfError(table: String) {
        if (status.isSuccess()) return
        val message = when (status) {
            HttpStatusCode.Unauthorized -> "Supabase authentication failed or session expired (HTTP 401)."
            HttpStatusCode.Forbidden -> "Supabase access denied by Row Level Security on '$table' (HTTP 403)."
            HttpStatusCode.NotFound -> "Supabase table '$table' not found (HTTP 404). Please verify your database schema."
            HttpStatusCode.TooManyRequests -> "Supabase rate limit exceeded (HTTP 429). Please wait a moment before syncing again."
            HttpStatusCode.InternalServerError -> "Supabase server error (HTTP 500). Please try again later."
            else -> "Supabase request to '$table' failed with HTTP ${status.value}: ${status.description}."
        }
        throw SyncHttpException(status.value, message)
    }

    override suspend fun authenticate(): Boolean {
        val email = config.userEmail
        val password = config.userPassword
        if (email.isNullOrBlank() || password.isNullOrBlank()) return false

        return try {
            val response = httpClient.post("$authUrl/token?grant_type=password") {
                header("apikey", config.anonKey)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(SupabaseAuthRequest(email, password)))
            }
            if (!response.status.isSuccess()) return false

            val authResp: SupabaseAuthResponse = response.body()
            if (authResp.accessToken.isBlank()) return false

            authToken = authResp.accessToken
            config.authToken = authResp.accessToken
            authResp.user?.id?.takeIf { it.isNotBlank() }?.let {
                userId = it
                config.userId = it
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun testConnection(): SyncConnectionTestResult {
        if (config.projectUrl.isBlank() || config.anonKey.isBlank()) {
            return SyncConnectionTestResult(success = false, errorMessage = "Project URL and API Key must be configured.")
        }

        if (!config.userEmail.isNullOrBlank() && !config.userPassword.isNullOrBlank()) {
            if (!authenticate()) {
                return SyncConnectionTestResult(success = false, errorMessage = "Authentication failed: invalid email or password.")
            }
        }

        return try {
            val response = httpClient.get("$baseUrl/vb_schema_info?select=version&limit=1") {
                header("apikey", config.anonKey)
                header("Authorization", "Bearer ${bearerToken()}")
            }

            when {
                response.status.isSuccess() -> SyncConnectionTestResult(success = true, tablesExist = true)
                response.status == HttpStatusCode.Unauthorized -> SyncConnectionTestResult(
                    success = false,
                    errorMessage = "Authentication failed: invalid anon/public API key or token (HTTP 401)."
                )
                response.status == HttpStatusCode.Forbidden -> SyncConnectionTestResult(
                    success = false,
                    errorMessage = "Access forbidden: Check Row Level Security policies (HTTP 403)."
                )
                response.status == HttpStatusCode.TooManyRequests -> SyncConnectionTestResult(
                    success = false,
                    errorMessage = "Supabase rate limit exceeded (HTTP 429). Please wait before connecting."
                )
                response.status == HttpStatusCode.NotFound -> SyncConnectionTestResult(
                    success = true,
                    tablesExist = false,
                    missingTables = listOf("vb_schema_info", "vb_bookmark_collections", "vb_bookmarks", "vb_highlights", "vb_notes"),
                    errorMessage = "Connected to Supabase project, but sync tables are not yet initialized. Please run supabase_schema.sql."
                )
                else -> SyncConnectionTestResult(
                    success = false,
                    errorMessage = "Connection returned HTTP ${response.status.value}: ${response.status.description}"
                )
            }
        } catch (ex: Exception) {
            SyncConnectionTestResult(success = false, errorMessage = "Could not connect to Supabase: ${ex.message}")
        }
    }

    private val syncLookbackBuffer: Duration = Duration.ofHours(24)

    override suspend fun pullChanges(sinceUtc: Instant?): RemoteChangeSet {
        val effectiveSince = sinceUtc?.minus(syncLookbackBuffer)
        val queryFilter = effectiveSince?.let {
            "&updated_at=gt.${URLEncoder.encode(IsoTime.format(it), "UTF-8")}"
        } ?: ""

        fun authedGet(path: String) = suspend {
            httpClient.get("$baseUrl/$path") {
                header("apikey", config.anonKey)
                header("Authorization", "Bearer ${bearerToken()}")
            }
        }

        val collectionsResp = authedGet("vb_bookmark_collections?select=*&order=updated_at.asc$queryFilter")()
        collectionsResp.throwIfError("vb_bookmark_collections")
        val collections: List<SupabaseCollectionDto> = json.decodeFromString(collectionsResp.bodyAsText())

        val bookmarksResp = authedGet("vb_bookmarks?select=*&order=updated_at.asc$queryFilter")()
        bookmarksResp.throwIfError("vb_bookmarks")
        val bookmarks: List<SupabaseBookmarkDto> = json.decodeFromString(bookmarksResp.bodyAsText())

        val highlightsResp = authedGet("vb_highlights?select=*&order=updated_at.asc$queryFilter")()
        highlightsResp.throwIfError("vb_highlights")
        val highlights: List<SupabaseHighlightDto> = json.decodeFromString(highlightsResp.bodyAsText())

        val notesResp = authedGet("vb_notes?select=*&order=updated_at.asc$queryFilter")()
        notesResp.throwIfError("vb_notes")
        val notes: List<SupabaseNoteDto> = json.decodeFromString(notesResp.bodyAsText())

        return RemoteChangeSet(
            collections = collections.map {
                BookmarkCollection(
                    id = it.id, name = it.name, sortOrder = it.sortOrder,
                    createdUtc = IsoTime.parse(it.createdAt), updatedUtc = IsoTime.parse(it.updatedAt),
                    deletedUtc = it.deletedAt?.let(IsoTime::parse)
                )
            },
            bookmarks = bookmarks.map {
                UserBookmark(
                    id = it.id, recordKey = it.recordKey, collectionId = it.collectionId, title = it.title,
                    createdUtc = IsoTime.parse(it.createdAt), updatedUtc = IsoTime.parse(it.updatedAt),
                    deletedUtc = it.deletedAt?.let(IsoTime::parse)
                )
            },
            highlights = highlights.map {
                Highlight(
                    id = it.id, recordKey = it.recordKey, field = it.field,
                    color = runCatching { HighlightColor.valueOf(it.color) }.getOrDefault(HighlightColor.Yellow),
                    startOffset = it.startOffset, length = it.length, selectedText = it.selectedText,
                    createdUtc = IsoTime.parse(it.createdAt), updatedUtc = IsoTime.parse(it.updatedAt),
                    deletedUtc = it.deletedAt?.let(IsoTime::parse)
                )
            },
            notes = notes.map {
                UserNote(
                    id = it.id, recordKey = it.recordKey, title = it.title, content = it.content,
                    field = it.field, startOffset = it.startOffset ?: -1, length = it.length ?: -1,
                    createdUtc = IsoTime.parse(it.createdAt), updatedUtc = IsoTime.parse(it.updatedAt),
                    deletedUtc = it.deletedAt?.let(IsoTime::parse)
                )
            }
        )
    }

    override suspend fun pushChanges(changes: LocalChangeSet): Int {
        if (changes.totalCount == 0) return 0
        var pushed = 0
        val uid = userId ?: config.userId

        if (changes.collections.isNotEmpty()) {
            val dtos = changes.collections.map {
                SupabaseCollectionDto(
                    id = it.id, userId = uid, name = it.name, sortOrder = it.sortOrder,
                    createdAt = IsoTime.format(it.createdUtc), updatedAt = IsoTime.format(it.updatedUtc),
                    deletedAt = it.deletedUtc?.let(IsoTime::format), deviceId = deviceId
                )
            }
            upsertBatch("vb_bookmark_collections", json.encodeToString(dtos))
            pushed += dtos.size
        }

        if (changes.bookmarks.isNotEmpty()) {
            val dtos = changes.bookmarks.map {
                SupabaseBookmarkDto(
                    id = it.id, userId = uid, recordKey = it.recordKey, collectionId = it.collectionId, title = it.title,
                    createdAt = IsoTime.format(it.createdUtc), updatedAt = IsoTime.format(it.updatedUtc),
                    deletedAt = it.deletedUtc?.let(IsoTime::format), deviceId = deviceId
                )
            }
            upsertBatch("vb_bookmarks", json.encodeToString(dtos))
            pushed += dtos.size
        }

        if (changes.highlights.isNotEmpty()) {
            val dtos = changes.highlights.map {
                SupabaseHighlightDto(
                    id = it.id, userId = uid, recordKey = it.recordKey, field = it.field, color = it.color.name,
                    startOffset = it.startOffset, length = it.length, selectedText = it.selectedText,
                    createdAt = IsoTime.format(it.createdUtc), updatedAt = IsoTime.format(it.updatedUtc),
                    deletedAt = it.deletedUtc?.let(IsoTime::format), deviceId = deviceId
                )
            }
            upsertBatch("vb_highlights", json.encodeToString(dtos))
            pushed += dtos.size
        }

        if (changes.notes.isNotEmpty()) {
            val dtos = changes.notes.map {
                SupabaseNoteDto(
                    id = it.id, userId = uid, recordKey = it.recordKey, title = it.title, content = it.content,
                    field = it.field,
                    startOffset = if (it.startOffset >= 0) it.startOffset else null,
                    length = if (it.length > 0) it.length else null,
                    createdAt = IsoTime.format(it.createdUtc), updatedAt = IsoTime.format(it.updatedUtc),
                    deletedAt = it.deletedUtc?.let(IsoTime::format), deviceId = deviceId
                )
            }
            upsertBatch("vb_notes", json.encodeToString(dtos))
            pushed += dtos.size
        }

        return pushed
    }

    private suspend fun upsertBatch(table: String, bodyJson: String) {
        val response = httpClient.post("$baseUrl/$table") {
            header("apikey", config.anonKey)
            header("Authorization", "Bearer ${bearerToken()}")
            header("Prefer", "resolution=merge-duplicates")
            contentType(ContentType.Application.Json)
            setBody(bodyJson)
        }
        response.throwIfError(table)
    }

    override suspend fun disconnect() {
        authToken = null
        userId = null
    }
}
