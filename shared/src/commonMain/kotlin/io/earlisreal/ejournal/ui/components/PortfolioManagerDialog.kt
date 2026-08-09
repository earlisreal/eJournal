package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.AlpacaBrokerCredentials
import io.earlisreal.ejournal.domain.alpaca.AlpacaConnectionResult
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.tradezero.TradeZeroConnectionResult
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.CardShape
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.BrokerConnectionTestResult
import io.earlisreal.ejournal.ui.viewmodel.BrokerCredentialDraft
import io.earlisreal.ejournal.ui.viewmodel.PortfolioManagerViewModel

@Composable
fun PortfolioManagerDialog(
    portfolioRepository: io.earlisreal.ejournal.data.repository.PortfolioRepository,
    transactionRepository: io.earlisreal.ejournal.data.repository.TransactionRepository,
    portfolioSettings: io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository,
    credentialsRepository: io.earlisreal.ejournal.data.repository.CredentialsRepository,
    alpacaBrokerClient: io.earlisreal.ejournal.domain.alpaca.AlpacaBrokerClient,
    tradeZeroClient: io.earlisreal.ejournal.domain.tradezero.TradeZeroClient,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val vm = viewModel {
        PortfolioManagerViewModel(
            portfolioRepository,
            transactionRepository,
            portfolioSettings,
            credentialsRepository,
            alpacaBrokerClient,
            tradeZeroClient,
            onChanged,
        )
    }
    val state by vm.state.collectAsState()

    var editingId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var market by remember { mutableStateOf(Market.US_STOCKS) }
    var broker by remember { mutableStateOf<Broker?>(null) }
    var alpacaKeyId by remember { mutableStateOf("") }
    var alpacaSecretKey by remember { mutableStateOf("") }
    var alpacaEnvironment by remember { mutableStateOf(io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment.PAPER) }
    var tradeZeroKeyId by remember { mutableStateOf("") }
    var tradeZeroSecretKey by remember { mutableStateOf("") }
    var brokerMenuExpanded by remember { mutableStateOf(false) }

    fun clearBrokerFields() {
        alpacaKeyId = ""
        alpacaSecretKey = ""
        tradeZeroKeyId = ""
        tradeZeroSecretKey = ""
        alpacaEnvironment = io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment.PAPER
        vm.clearConnectionTest()
    }

    fun resetForm() {
        editingId = null
        name = ""
        market = Market.US_STOCKS
        broker = null
        clearBrokerFields()
    }

    fun startEdit(portfolio: io.earlisreal.ejournal.domain.model.Portfolio) {
        editingId = portfolio.id
        name = portfolio.name
        market = portfolio.market
        broker = portfolio.broker
        clearBrokerFields()
        when (val draft = vm.draftFor(portfolio)) {
            is BrokerCredentialDraft.Alpaca -> {
                alpacaKeyId = draft.keyId
                alpacaSecretKey = draft.secretKey
                alpacaEnvironment = draft.environment
            }
            is BrokerCredentialDraft.TradeZero -> {
                tradeZeroKeyId = draft.keyId
                tradeZeroSecretKey = draft.secretKey
            }
            null -> Unit
        }
    }

    fun currentDraft(): BrokerCredentialDraft? = when (broker) {
        Broker.ALPACA -> BrokerCredentialDraft.Alpaca(alpacaKeyId, alpacaSecretKey, alpacaEnvironment)
        Broker.TRADEZERO -> BrokerCredentialDraft.TradeZero(tradeZeroKeyId, tradeZeroSecretKey)
        null -> null
    }

    fun draftIsValid(): Boolean {
        val draft = currentDraft() ?: return true
        return (draft.keyId.isBlank() && draft.secretKey.isBlank()) ||
            (draft.keyId.isNotBlank() && draft.secretKey.isNotBlank())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = CardShape, color = AppTheme.colors.surface) {
            Column(
                modifier = Modifier.width(560.dp).heightIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text("Portfolios", color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.headlineSmall)

                state.portfolios.forEach { portfolio ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(portfolio.name, color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${portfolio.market.label} · ${portfolio.broker?.label ?: "Manual import"}",
                                color = AppTheme.colors.textMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            AppTextButton(text = "Edit", onClick = { startEdit(portfolio) })
                            AppTextButton(text = "Delete", onClick = { vm.requestDelete(portfolio) })
                        }
                    }
                }

                if (state.portfolios.isEmpty()) {
                    Text("No portfolios yet. Add one below.", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
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
                MarketDropdown(
                    selected = market,
                    onSelect = {
                        market = it
                        if (it != Market.US_STOCKS) {
                            broker = null
                            clearBrokerFields()
                        }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("Broker", color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    AppSecondaryButton(
                        text = broker?.label ?: "None / Manual Import",
                        onClick = { brokerMenuExpanded = true },
                    )
                    DropdownMenu(expanded = brokerMenuExpanded, onDismissRequest = { brokerMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("None / Manual Import") },
                            onClick = { broker = null; clearBrokerFields(); brokerMenuExpanded = false },
                        )
                        if (market == Market.US_STOCKS) {
                            Broker.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { broker = option; clearBrokerFields(); brokerMenuExpanded = false },
                                )
                            }
                        }
                    }
                }

                if (broker != null) {
                    HorizontalDivider(color = AppTheme.colors.border)
                    Text("Broker configuration", color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.titleSmall)
                    when (broker) {
                        Broker.ALPACA -> AlpacaBrokerForm(
                            keyId = alpacaKeyId,
                            secretKey = alpacaSecretKey,
                            environment = alpacaEnvironment,
                            onKeyId = { alpacaKeyId = it; vm.clearConnectionTest() },
                            onSecretKey = { alpacaSecretKey = it; vm.clearConnectionTest() },
                            onEnvironment = { alpacaEnvironment = it; vm.clearConnectionTest() },
                            global = vm.globalAlpacaCredentials(),
                            onCopyGlobal = { vm.globalAlpacaCredentials()?.let { alpacaKeyId = it.keyId; alpacaSecretKey = it.secretKey } },
                        )
                        Broker.TRADEZERO -> TradeZeroBrokerForm(
                            keyId = tradeZeroKeyId,
                            secretKey = tradeZeroSecretKey,
                            onKeyId = { tradeZeroKeyId = it; vm.clearConnectionTest() },
                            onSecretKey = { tradeZeroSecretKey = it; vm.clearConnectionTest() },
                        )
                        null -> Unit
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        AppSecondaryButton(
                            text = if (state.testingConnection) "Testing…" else "Test Connection",
                            onClick = { vm.testConnection(currentDraft()) },
                            enabled = !state.testingConnection && draftIsValid() && currentDraft()?.keyId?.isNotBlank() == true,
                        )
                        state.connectionTest?.let { BrokerConnectionResultText(it) }
                    }
                } else {
                    Text(
                        "Manual CSV/XLSX import is available without broker credentials.",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                state.error?.let { Text(it, color = AppTheme.colors.loss, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AppPrimaryButton(
                        text = if (editingId == null) "Add" else "Save",
                        enabled = name.isNotBlank() && draftIsValid(),
                        onClick = {
                            val draft = currentDraft()
                            editingId?.let { vm.update(it, name, market, broker, draft) }
                                ?: vm.create(name, market, broker, draft)
                            resetForm()
                        },
                    )
                    if (editingId != null) AppTextButton(text = "Cancel", onClick = { resetForm() })
                }
                Text(
                    "Broker sync currently supports US Stocks. Changing broker settings preserves historical transactions.",
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                AppTextButton(text = "Close", onClick = onDismiss, modifier = Modifier.align(Alignment.End))
            }
        }
    }

    state.pendingDelete?.let { portfolio ->
        Dialog(onDismissRequest = { vm.cancelDelete() }) {
            Surface(shape = CardShape, color = AppTheme.colors.surface) {
                Column(Modifier.width(420.dp).padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("Delete ${portfolio.name}?", color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.pendingDeleteCount == 0L) "This portfolio has no transactions."
                        else "This also deletes ${state.pendingDeleteCount} transaction(s). This can't be undone.",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
                        AppTextButton(text = "Cancel", onClick = { vm.cancelDelete() })
                        AppTextButton(text = "Delete", onClick = { vm.confirmDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun AlpacaBrokerForm(
    keyId: String,
    secretKey: String,
    environment: io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment,
    onKeyId: (String) -> Unit,
    onSecretKey: (String) -> Unit,
    onEnvironment: (io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment) -> Unit,
    global: io.earlisreal.ejournal.data.repository.AlpacaMarketDataCredentials?,
    onCopyGlobal: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        EnvironmentToggle(environment, onEnvironment)
        SecretKeyTextField(value = keyId, onValueChange = onKeyId, label = "API Key ID")
        SecretKeyTextField(value = secretKey, onValueChange = onSecretKey, label = "Secret Key")
        AppSecondaryButton(text = "Copy global Alpaca keys", onClick = onCopyGlobal, enabled = global != null)
        if (global == null) Text("Configure global Alpaca market-data keys in Settings to enable copy.", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
        Text("Credentials are optional. Paper/Live belongs to this portfolio.", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TradeZeroBrokerForm(
    keyId: String,
    secretKey: String,
    onKeyId: (String) -> Unit,
    onSecretKey: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SecretKeyTextField(value = keyId, onValueChange = onKeyId, label = "API Key ID")
        SecretKeyTextField(value = secretKey, onValueChange = onSecretKey, label = "Secret Key")
        Text("Credentials are optional and stored only for this portfolio.", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BrokerConnectionResultText(result: BrokerConnectionTestResult) {
    val (text, color) = when (result) {
        is BrokerConnectionTestResult.Alpaca -> when (val value = result.result) {
            is AlpacaConnectionResult.Connected -> {
                val suffix = value.account.accountNumber?.takeLast(4)?.let { " ••••$it" }.orEmpty()
                "✓ Connected · ${value.environment.label} account$suffix" to AppTheme.colors.profit
            }
            AlpacaConnectionResult.InvalidCredentials -> "✗ Invalid credentials" to AppTheme.colors.loss
            is AlpacaConnectionResult.NetworkError -> "✗ Network error" to AppTheme.colors.loss
        }
        is BrokerConnectionTestResult.TradeZero -> when (val value = result.result) {
            is TradeZeroConnectionResult.Connected -> "✓ Connected · account ${value.account.id}" to AppTheme.colors.profit
            TradeZeroConnectionResult.InvalidCredentials -> "✗ Invalid credentials" to AppTheme.colors.loss
            is TradeZeroConnectionResult.NetworkError -> "✗ Network error" to AppTheme.colors.loss
        }
    }
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun EnvironmentToggle(
    environment: io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment,
    onChange: (io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment.entries.forEach { option ->
            AppTextButton(text = if (option == environment) "✓ ${option.label}" else option.label, onClick = { onChange(option) })
        }
    }
}

@Composable
private fun SecretKeyTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}
