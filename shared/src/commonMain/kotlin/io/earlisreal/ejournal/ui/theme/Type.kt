package io.earlisreal.ejournal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The monospaced family used for every figure — JetBrains Mono, loaded and provided by [AppTheme].
 * Falls back to the platform monospace when read outside a theme (e.g. a bare preview).
 */
val LocalMonoFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

/**
 * Deliberate scale on top of Material's defaults. Two roles are tuned rather than left stock:
 * screen titles get a tighter, heavier cut so they read as set type, not a default label; small
 * labels (the uppercase section eyebrows) get positive tracking so they read as small-caps signage.
 * Everything monetary uses the monospaced [NumberTextStyle]/[HeroNumberTextStyle] — the figures are
 * the app's identity, so they carry it consistently.
 */
private val base = Typography()

val AppTypography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
)

private val baseNumberStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp)

/** Tabular figures so P&L / price columns align vertically. Use for all monetary/numeric cells. */
val NumberTextStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = baseNumberStyle.copy(fontFamily = LocalMonoFontFamily.current)

private val baseHeroNumberStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 44.sp, letterSpacing = (-1).sp)

/** Oversized monospaced figure for the dashboard's hero P&L — a ticker readout, not a tile value. */
val HeroNumberTextStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = baseHeroNumberStyle.copy(fontFamily = LocalMonoFontFamily.current)
