package com.prabhupadaconnect.vedabase.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android Keystore-backed storage for Supabase credentials (anon key,
 * password, auth/refresh tokens, user id) - the Android equivalent of the
 * desktop app's `WindowsCredentialStorageService` (Windows Credential
 * Manager). Never plain SharedPreferences: [EncryptedSharedPreferences]
 * wraps both keys and values with a hardware-backed master key.
 */
@Singleton
class CredentialStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "vedabase_secure_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun getCredential(key: String): String? = withContext(Dispatchers.IO) { prefs.getString(key, null) }

    suspend fun saveCredential(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).apply()
    }

    suspend fun deleteCredential(key: String): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).apply()
    }
}
