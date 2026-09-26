package com.prabhupadaconnect.vedabase.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prabhupadaconnect.vedabase.core.model.AppSettings
import com.prabhupadaconnect.vedabase.core.model.AppTheme
import com.prabhupadaconnect.vedabase.core.model.ReadingFontSize
import com.prabhupadaconnect.vedabase.core.model.ReadingLineSpacing
import com.prabhupadaconnect.vedabase.core.model.ReadingWidthOption
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reading_settings")

/**
 * The user's reading-experience preferences (theme, font size, line
 * spacing, reading width, focus mode, block visibility toggles) - lives
 * entirely in DataStore, never mixed with corpus content or research data.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val READING_WIDTH = stringPreferencesKey("reading_width")
        val LINE_SPACING = stringPreferencesKey("line_spacing")
        val FOCUS_MODE = booleanPreferencesKey("focus_mode_enabled")
        val SHOW_TRANSLITERATION = booleanPreferencesKey("show_transliteration")
        val SHOW_SYNONYMS = booleanPreferencesKey("show_synonyms")
        val SHOW_PURPORT = booleanPreferencesKey("show_purport")
        val SHOW_PRONUNCIATION_GUIDE = booleanPreferencesKey("show_pronunciation_guide")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.System,
            fontSize = prefs[Keys.FONT_SIZE]?.let { runCatching { ReadingFontSize.valueOf(it) }.getOrNull() } ?: ReadingFontSize.Medium,
            readingWidth = prefs[Keys.READING_WIDTH]?.let { runCatching { ReadingWidthOption.valueOf(it) }.getOrNull() } ?: ReadingWidthOption.Comfortable,
            lineSpacing = prefs[Keys.LINE_SPACING]?.let { runCatching { ReadingLineSpacing.valueOf(it) }.getOrNull() } ?: ReadingLineSpacing.Comfortable,
            focusModeEnabled = prefs[Keys.FOCUS_MODE] ?: false,
            showTransliteration = prefs[Keys.SHOW_TRANSLITERATION] ?: true,
            showSynonyms = prefs[Keys.SHOW_SYNONYMS] ?: true,
            showPurport = prefs[Keys.SHOW_PURPORT] ?: true,
            showPronunciationGuide = prefs[Keys.SHOW_PRONUNCIATION_GUIDE] ?: true
        )
    }

    suspend fun setTheme(theme: AppTheme) = context.dataStore.edit { it[Keys.THEME] = theme.name }
    suspend fun setFontSize(size: ReadingFontSize) = context.dataStore.edit { it[Keys.FONT_SIZE] = size.name }
    suspend fun setReadingWidth(width: ReadingWidthOption) = context.dataStore.edit { it[Keys.READING_WIDTH] = width.name }
    suspend fun setLineSpacing(spacing: ReadingLineSpacing) = context.dataStore.edit { it[Keys.LINE_SPACING] = spacing.name }
    suspend fun setFocusMode(enabled: Boolean) = context.dataStore.edit { it[Keys.FOCUS_MODE] = enabled }
    suspend fun setShowTransliteration(show: Boolean) = context.dataStore.edit { it[Keys.SHOW_TRANSLITERATION] = show }
    suspend fun setShowSynonyms(show: Boolean) = context.dataStore.edit { it[Keys.SHOW_SYNONYMS] = show }
    suspend fun setShowPurport(show: Boolean) = context.dataStore.edit { it[Keys.SHOW_PURPORT] = show }
    suspend fun setShowPronunciationGuide(show: Boolean) = context.dataStore.edit { it[Keys.SHOW_PRONUNCIATION_GUIDE] = show }
}
