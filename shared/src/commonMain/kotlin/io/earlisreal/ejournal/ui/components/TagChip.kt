package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.model.Tag

/** Parses a "#RRGGBB" hex string into an opaque [Color]; falls back to slate gray on malformed input. */
fun tagColor(hex: String): Color {
    val rgb = hex.removePrefix("#").toLongOrNull(16) ?: return Color(0xFF64748B)
    return Color(0xFF000000 or (rgb and 0xFFFFFF))
}

/** A small colored dot used to preview a tag's color in pickers and lists. */
@Composable
fun TagColorDot(color: String, size: Int = 9, modifier: Modifier = Modifier) {
    Box(modifier.size(size.dp).clip(CircleShape).background(tagColor(color)))
}

/** A compact tag chip: a color dot + the tag name, tinted with the tag's color. */
@Composable
fun TagChip(
    tag: Tag,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    val c = tagColor(tag.color)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c))
        Text(tag.name, color = c, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        if (onRemove != null) {
            Text(
                "×",
                color = c,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onRemove() }.padding(start = 1.dp),
            )
        }
    }
}
