package io.earlisreal.ejournal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    /** Main content canvas; the active nav-item highlight matches it so selection bleeds into the content. */
    val contentBackground: Color,
    /** Left sidebar background. */
    val sidebarBackground: Color,
    val border: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val profit: Color,
    val loss: Color,
)

val lightAppColors = AppColors(
    background = Color(0xFFF4F5F9),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    contentBackground = Color(0xFFFFFFFF),
    sidebarBackground = Color(0xFFF4F5F9),
    border = Color(0xFFE7E9F0),
    textPrimary = Color(0xFF1B1F2A),
    textMuted = Color(0xFF6B7180),
    accent = Color(0xFFD99125),
    onAccent = Color(0xFFFFFFFF),
    profit = Color(0xFF16A34A),
    loss = Color(0xFFDC2626),
)

val darkAppColors = AppColors(
    background = Color(0xFF0F1115),
    surface = Color(0xFF171A21),
    surfaceElevated = Color(0xFF1E222B),
    contentBackground = Color(0xFF0F1115),
    sidebarBackground = Color(0xFF171A21),
    border = Color(0xFF2A2F3A),
    textPrimary = Color(0xFFE6E9EF),
    textMuted = Color(0xFF9AA3B2),
    accent = Color(0xFFE0A340),
    onAccent = Color(0xFF1B1300),
    profit = Color(0xFF3FB950),
    loss = Color(0xFFF85149),
)

val LocalAppColors = staticCompositionLocalOf { lightAppColors }
