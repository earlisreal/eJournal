package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.CardShape
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.PortfolioManagerViewModel

@Composable
fun PortfolioManagerDialog(
    portfolioRepository: PortfolioRepository,
    transactionRepository: TransactionRepository,
    portfolioSettings: PortfolioSettingsRepository,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val vm = viewModel { PortfolioManagerViewModel(portfolioRepository, transactionRepository, portfolioSettings, onChanged) }
    val state by vm.state.collectAsState()

    var editingId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var market by remember { mutableStateOf(Market.US_STOCKS) }

    fun resetForm() {
        editingId = null; name = ""; market = Market.US_STOCKS
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = CardShape, color = AppTheme.colors.surface) {
            Column(
                modifier = Modifier.width(440.dp).padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text("Portfolios", color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                if (state.portfolios.isEmpty()) {
                    Text("No portfolios yet. Add one below.", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    state.portfolios.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(p.name, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(p.market.label, color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                AppTextButton(text = "Edit", onClick = { editingId = p.id; name = p.name; market = p.market })
                                AppTextButton(text = "Delete", onClick = { vm.requestDelete(p) })
                            }
                        }
                    }
                }

                HorizontalDivider(color = AppTheme.colors.border)

                Text(
                    if (editingId == null) "Add portfolio" else "Edit portfolio",
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MarketDropdown(selected = market, onSelect = { market = it })
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AppPrimaryButton(
                        text = if (editingId == null) "Add" else "Save",
                        enabled = name.isNotBlank(),
                        onClick = {
                            val id = editingId
                            if (id == null) vm.create(name, market) else vm.update(id, name, market)
                            resetForm()
                        },
                    )
                    if (editingId != null) AppTextButton(text = "Cancel", onClick = { resetForm() })
                }

                Text(
                    "Market is a label and a scraper hint — changing it does not convert past P&L.",
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )

                AppTextButton(text = "Close", onClick = onDismiss, modifier = Modifier.align(Alignment.End))
            }
        }
    }

    state.pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            title = { Text("Delete ${p.name}?") },
            text = {
                Text(
                    if (state.pendingDeleteCount == 0L) "This portfolio has no transactions."
                    else "This also deletes ${state.pendingDeleteCount} transaction(s). This can't be undone.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.confirmDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { vm.cancelDelete() }) { Text("Cancel") } },
        )
    }
}
