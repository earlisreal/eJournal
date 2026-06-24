package io.earlisreal.ejournal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ejournal.shared.generated.resources.Res
import ejournal.shared.generated.resources.jetbrains_mono_medium
import ejournal.shared.generated.resources.jetbrains_mono_regular
import org.jetbrains.compose.resources.Font

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

@Composable
fun AppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) darkAppColors else lightAppColors

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = appColors.accent,
            onPrimary = appColors.onAccent,
            background = appColors.background,
            onBackground = appColors.textPrimary,
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.surfaceElevated,
            onSurfaceVariant = appColors.textMuted,
            outline = appColors.border,
            outlineVariant = appColors.border,
            error = appColors.loss,
        )
    } else {
        lightColorScheme(
            primary = appColors.accent,
            onPrimary = appColors.onAccent,
            background = appColors.background,
            onBackground = appColors.textPrimary,
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.surfaceElevated,
            onSurfaceVariant = appColors.textMuted,
            outline = appColors.border,
            outlineVariant = appColors.border,
            error = appColors.loss,
        )
    }

    val monoFontFamily = FontFamily(
        Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
    )

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalMonoFontFamily provides monoFontFamily,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
