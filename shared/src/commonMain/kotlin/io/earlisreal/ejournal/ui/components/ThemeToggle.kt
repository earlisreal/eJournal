package io.earlisreal.ejournal.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.ThemeMode

@Composable
fun ThemeToggle(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val next = when (mode) {
        ThemeMode.SYSTEM -> ThemeMode.LIGHT
        ThemeMode.LIGHT -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.SYSTEM
    }
    val glyph = when (mode) {
        ThemeMode.SYSTEM -> "Auto"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
    TextButton(onClick = { onModeChange(next) }, modifier = modifier) {
        Text(glyph, color = AppTheme.colors.textMuted)
    }
}
