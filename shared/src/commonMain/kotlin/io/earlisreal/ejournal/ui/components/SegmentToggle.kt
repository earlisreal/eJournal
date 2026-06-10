package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.PillShape

/** All / Day / Swing segmented control. */
@Composable
fun SegmentToggle(
    segment: Segment,
    onSegmentChange: (Segment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(AppTheme.colors.surfaceElevated),
    ) {
        Segment.entries.forEach { option ->
            val active = option == segment
            Text(
                text = when (option) {
                    Segment.ALL -> "All"
                    Segment.DAY -> "Day"
                    Segment.SWING -> "Swing"
                },
                color = if (active) AppTheme.colors.onAccent else AppTheme.colors.textMuted,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) AppTheme.colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSegmentChange(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
