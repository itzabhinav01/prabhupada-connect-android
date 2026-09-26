package com.prabhupadaconnect.vedabase.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** PostgREST snake_case DTOs mirroring the desktop app's `vb_*` Supabase tables 1:1. */

@Serializable
data class SupabaseCollectionDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class SupabaseBookmarkDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("record_key") val recordKey: String,
    @SerialName("collection_id") val collectionId: String? = null,
    val title: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class SupabaseHighlightDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("record_key") val recordKey: String,
    val field: String,
    val color: String,
    @SerialName("start_offset") val startOffset: Int = -1,
    val length: Int = -1,
    @SerialName("selected_text") val selectedText: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class SupabaseNoteDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("record_key") val recordKey: String? = null,
    val title: String? = null,
    val content: String,
    val field: String? = null,
    @SerialName("start_offset") val startOffset: Int? = null,
    val length: Int? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class SupabaseSchemaInfoDto(
    val version: Int,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseAuthRequest(val email: String, val password: String)

@Serializable
data class SupabaseAuthResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String = "",
    @SerialName("expires_in") val expiresIn: Int = 0,
    val user: SupabaseUserInfoDto? = null
)

@Serializable
data class SupabaseUserInfoDto(
    val id: String = "",
    val email: String? = null
)
