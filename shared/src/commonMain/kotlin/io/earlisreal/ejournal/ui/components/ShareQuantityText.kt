package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareQuantityText(
    shares: Double,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    alignment: Alignment = Alignment.TopStart,
) {
    val (label, fullQuantity) = remember(shares) { formatShareQuantity(shares) }
    val text: @Composable () -> Unit = { Text(prefix + label + suffix, color = color, style = style) }
    Box(modifier = modifier, contentAlignment = alignment) {
        if (fullQuantity == null) {
            text()
        } else {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(fullQuantity) } },
                state = rememberTooltipState(),
                content = text,
            )
        }
    }
}
