package com.prabhupadaconnect.vedabase.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.prabhupadaconnect.vedabase.core.model.AppTheme

// Sepia is the app's own "focus mode" palette for long-form scripture
// reading - not Material You dynamic, deliberately warm and low-glare.
private val SepiaColorScheme = lightColorScheme(
    primary = Color(0xFF8B5E34),
    onPrimary = Color(0xFFFFFBF0),
    secondary = Color(0xFFB8860B),
    background = Color(0xFFFBF0D9),
    onBackground = Color(0xFF3B2F1E),
    surface = Color(0xFFF6E8C9),
    onSurface = Color(0xFF3B2F1E),
    surfaceVariant = Color(0xFFEEDDB0),
    onSurfaceVariant = Color(0xFF554527)
)

private val DefaultLightColorScheme = lightColorScheme(
    primary = Color(0xFF8B5E34),
    secondary = Color(0xFFB8860B)
)

private val DefaultDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD4A76A),
    secondary = Color(0xFFE0B84A)
)

/** Sanskrit chanting-verse text color - a luminous golden yellow tuned for contrast in both light and dark. */
val SanskritChantingColor = Color(0xFFC9A227)

@Composable
fun VedaBaseTheme(
    theme: AppTheme = AppTheme.System,
    content: @Composable () -> Unit
) {
    val darkSystem = isSystemInDarkTheme()
    val useDark = when (theme) {
        AppTheme.System -> darkSystem
        AppTheme.Dark -> true
        AppTheme.Light, AppTheme.Sepia -> false
    }

    val context = LocalContext.current
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        theme == AppTheme.Sepia -> SepiaColorScheme
        dynamicSupported && useDark -> dynamicDarkColorScheme(context)
        dynamicSupported && !useDark -> dynamicLightColorScheme(context)
        useDark -> DefaultDarkColorScheme
        else -> DefaultLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VedaBaseTypography,
        content = content
    )
}
