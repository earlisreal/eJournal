package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.let { "${it.name} · ${it.market.label}" } ?: "No portfolio"
    Box(modifier = modifier) {
        Pill(
            text = "▾ $label",
            modifier = Modifier.clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            portfolios.forEach { p ->
                DropdownMenuItem(
                    text = { Text("${p.name} · ${p.market.label}") },
                    onClick = { onSelect(p); expanded = false },
                )
            }
            if (portfolios.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Manage portfolios…") },
                onClick = { onManage(); expanded = false },
            )
        }
    }
}
