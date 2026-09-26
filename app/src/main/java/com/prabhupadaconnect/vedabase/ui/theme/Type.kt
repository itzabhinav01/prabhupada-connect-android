package com.prabhupadaconnect.vedabase.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.prabhupadaconnect.vedabase.R
import com.prabhupadaconnect.vedabase.core.model.ReadingFontSize
import com.prabhupadaconnect.vedabase.core.model.ReadingLineSpacing

// res/font/noto_sans_devanagari.ttf is Google's Noto Sans Devanagari
// (OFL-licensed; see assets/licenses/NotoSansDevanagari-OFL.txt), a variable
// font (wdth+wght axes). minSdk 26 renders variable-font TTFs natively, so no
// static-instance fallback is needed - this gives correct conjunct/ligature
// shaping for Devanagari-script Sanskrit citations that the platform's
// default font does not reliably provide across OEM skins.
val DevanagariFontFamily: FontFamily = FontFamily(Font(R.font.noto_sans_devanagari))

val VedaBaseTypography = Typography()

fun readingFontSizeSp(size: ReadingFontSize): Int = when (size) {
    ReadingFontSize.Small -> 15
    ReadingFontSize.Medium -> 17
    ReadingFontSize.Large -> 20
    ReadingFontSize.ExtraLarge -> 24
}

fun readingLineHeightMultiplier(spacing: ReadingLineSpacing): Float = when (spacing) {
    ReadingLineSpacing.Compact -> 1.25f
    ReadingLineSpacing.Comfortable -> 1.5f
    ReadingLineSpacing.Relaxed -> 1.85f
}

fun devanagariTextStyle(fontSize: ReadingFontSize, lineSpacing: ReadingLineSpacing): TextStyle {
    val sizeSp = readingFontSizeSp(fontSize) + 3
    return TextStyle(
        fontFamily = DevanagariFontFamily,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * readingLineHeightMultiplier(lineSpacing)).sp
    )
}

fun transliterationTextStyle(fontSize: ReadingFontSize, lineSpacing: ReadingLineSpacing): TextStyle {
    val sizeSp = readingFontSizeSp(fontSize)
    return TextStyle(
        fontStyle = FontStyle.Italic,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * readingLineHeightMultiplier(lineSpacing)).sp
    )
}

fun translationTextStyle(fontSize: ReadingFontSize, lineSpacing: ReadingLineSpacing): TextStyle {
    val sizeSp = readingFontSizeSp(fontSize) + 1
    return TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * readingLineHeightMultiplier(lineSpacing)).sp
    )
}

fun bodyTextStyle(fontSize: ReadingFontSize, lineSpacing: ReadingLineSpacing): TextStyle {
    val sizeSp = readingFontSizeSp(fontSize)
    return TextStyle(
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * readingLineHeightMultiplier(lineSpacing)).sp
    )
}
