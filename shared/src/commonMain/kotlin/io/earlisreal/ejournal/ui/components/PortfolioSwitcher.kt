package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.clickable
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
import io.earlisreal.ejournal.domain.model.Portfolio

@Composable
fun PortfolioSwitcher(
    portfolios: List<Portfolio>,
    selected: Portfolio?,
    onSelect: (Portfolio) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.let { "${it.name} · ${it.currency}" } ?: "No portfolio"
    Box(modifier = modifier) {
        Pill(
            text = "▾ $label",
            modifier = Modifier.clickable(enabled = portfolios.isNotEmpty()) { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            portfolios.forEach { p ->
                DropdownMenuItem(
                    text = { Text("${p.name} · ${p.currency}") },
                    onClick = { onSelect(p); expanded = false },
                )
            }
        }
    }
}
