package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.PillShape

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Text(
        text,
        color = AppTheme.colors.textPrimary,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .background(AppTheme.colors.surfaceElevated, PillShape)
            .border(
                if (emphasized) 0.dp else 1.dp,
                AppTheme.colors.border,
                PillShape,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
