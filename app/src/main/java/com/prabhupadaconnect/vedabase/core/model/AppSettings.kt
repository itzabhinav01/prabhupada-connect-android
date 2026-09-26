package com.prabhupadaconnect.vedabase.core.model

enum class AppTheme { System, Light, Dark, Sepia }
enum class ReadingFontSize { Small, Medium, Large, ExtraLarge }
enum class ReadingWidthOption { Narrow, Comfortable, Wide }
enum class ReadingLineSpacing { Compact, Comfortable, Relaxed }

/**
 * The user's personal reading-experience preferences, persisted via
 * Jetpack DataStore - never mixed into the read-only corpus database.
 */
data class AppSettings(
    val theme: AppTheme = AppTheme.System,
    val fontSize: ReadingFontSize = ReadingFontSize.Medium,
    val readingWidth: ReadingWidthOption = ReadingWidthOption.Comfortable,
    val lineSpacing: ReadingLineSpacing = ReadingLineSpacing.Comfortable,
    val focusModeEnabled: Boolean = false,
    val showTransliteration: Boolean = true,
    val showSynonyms: Boolean = true,
    val showPurport: Boolean = true,
    val showPronunciationGuide: Boolean = true
)
