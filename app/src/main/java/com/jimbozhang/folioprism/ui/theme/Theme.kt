package com.jimbozhang.folioprism.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val FolioGold = Color(0xFFE6A23C)
val FolioGoldSoft = Color(0xFFFDF2E3)
val FolioCoral = Color(0xFFE8613C)
val FolioGreen = Color(0xFF2E9E6B)
val FolioBlue = Color(0xFF0984E3)
val FolioPurple = Color(0xFF6C5CE7)
val FolioInk = Color(0xFF222222)
val FolioMuted = Color(0xFF8A8A8A)
val FolioDivider = Color(0xFFEEEEEE)
val FolioBackground = Color(0xFFF7F8F7)

private val FolioLightColors = lightColorScheme(
    primary = FolioGold,
    onPrimary = Color(0xFF2B1D0E),
    primaryContainer = FolioGoldSoft,
    onPrimaryContainer = Color(0xFF4A2E00),
    secondary = FolioCoral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCEAE4),
    onSecondaryContainer = Color(0xFF5A1B0D),
    tertiary = FolioGreen,
    onTertiary = Color.White,
    background = FolioBackground,
    onBackground = FolioInk,
    surface = Color.White,
    onSurface = FolioInk,
    surfaceVariant = Color(0xFFF3F4F3),
    onSurfaceVariant = FolioMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFFAFAFA),
    surfaceContainerHighest = Color(0xFFF3F4F3),
    outlineVariant = FolioDivider,
    error = FolioCoral,
    onError = Color.White,
)

private val FolioTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 40.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp),
)

@Composable
fun FolioPrismTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FolioLightColors,
        typography = FolioTypography,
        content = content,
    )
}
