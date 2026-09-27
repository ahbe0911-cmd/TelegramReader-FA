package com.telegramreader.fa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.telegramreader.fa.R

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, weight = FontWeight.Normal),
    Font(R.font.vazirmatn_medium, weight = FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, weight = FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, weight = FontWeight.Bold),
)

private val PersianLocale = LocaleList(Locale("fa-IR"))

private fun TextStyle.fa(
    weight: FontWeight? = null,
): TextStyle = copy(
    fontFamily = Vazirmatn,
    fontWeight = weight ?: fontWeight,
    localeList = PersianLocale,
)

private val BaseTypography = Typography()

val PersianTypography = Typography(
    displayLarge = BaseTypography.displayLarge.fa(),
    displayMedium = BaseTypography.displayMedium.fa(),
    displaySmall = BaseTypography.displaySmall.fa(),
    headlineLarge = BaseTypography.headlineLarge.fa(),
    headlineMedium = BaseTypography.headlineMedium.fa(),
    headlineSmall = BaseTypography.headlineSmall.fa(),
    titleLarge = BaseTypography.titleLarge.fa(FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.fa(FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.fa(FontWeight.Medium),
    bodyLarge = BaseTypography.bodyLarge.fa(),
    bodyMedium = BaseTypography.bodyMedium.fa(),
    bodySmall = BaseTypography.bodySmall.fa(),
    labelLarge = BaseTypography.labelLarge.fa(FontWeight.Medium),
    labelMedium = BaseTypography.labelMedium.fa(FontWeight.Medium),
    labelSmall = BaseTypography.labelSmall.fa(FontWeight.Medium),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1778F2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAFF),
    onPrimaryContainer = Color(0xFF082F63),
    secondary = Color(0xFF53657A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1EAF4),
    onSecondaryContainer = Color(0xFF172534),
    tertiary = Color(0xFF2D7D69),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8F2DD),
    onTertiaryContainer = Color(0xFF00382D),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF16181C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181C),
    surfaceVariant = Color(0xFFE9EEF5),
    onSurfaceVariant = Color(0xFF59616D),
    outline = Color(0xFFCFD6E0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF88B7FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF0A4D91),
    onPrimaryContainer = Color(0xFFDCEAFF),
    secondary = Color(0xFFB8C7D9),
    onSecondary = Color(0xFF243546),
    secondaryContainer = Color(0xFF35485A),
    onSecondaryContainer = Color(0xFFDCE8F7),
    tertiary = Color(0xFF79D7BD),
    onTertiary = Color(0xFF00382D),
    tertiaryContainer = Color(0xFF145846),
    onTertiaryContainer = Color(0xFFB8F2DD),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF17191E),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF24282F),
    onSurfaceVariant = Color(0xFFB9C0CA),
    outline = Color(0xFF414750),
)

private val ReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TelegramReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PersianTypography,
        shapes = ReaderShapes,
        content = content,
    )
}
