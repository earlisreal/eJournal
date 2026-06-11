package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.earlisreal.ejournal.domain.model.Market

@Composable
fun MarketDropdown(
    selected: Market,
    onSelect: (Market) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        AppSecondaryButton(text = selected.label, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Market.entries.forEach { market ->
                DropdownMenuItem(
                    text = { Text(market.label) },
                    onClick = { onSelect(market); expanded = false },
                )
            }
        }
    }
}
