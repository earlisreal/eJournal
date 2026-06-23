package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.domain.marketdata.AlpacaProvider
import io.earlisreal.ejournal.domain.marketdata.ConnectionResult
import io.earlisreal.ejournal.domain.marketdata.MarketDataService
import io.earlisreal.ejournal.domain.tradezero.TradeZeroClient
import io.earlisreal.ejournal.ui.components.AppCard
import io.earlisreal.ejournal.ui.components.AppPrimaryButton
import io.earlisreal.ejournal.ui.components.AppSecondaryButton
import io.earlisreal.ejournal.ui.components.MarketDataSyncStatus
import io.earlisreal.ejournal.ui.components.ScreenScaffold
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.PillShape
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.theme.ThemeMode
import io.earlisreal.ejournal.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    credentialsRepository: CredentialsRepository,
    alpacaProvider: AlpacaProvider,
    marketDataService: MarketDataService,
    tradeZeroClient: TradeZeroClient,
) {
    val vm = viewModel { SettingsViewModel(credentialsRepository, alpacaProvider, tradeZeroClient) }
    val state by vm.state.collectAsState()
    val syncStatus by marketDataService.status.collectAsState()

    ScreenScaffold(title = "Settings") {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            AppCard {
                SectionTitle("Appearance")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("Theme", color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    ThemeModeToggle(themeMode, onThemeChange)
                }
            }

            AppCard {
                SectionTitle("Market Data")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "Daily prices come from Yahoo Finance — no setup needed. " +
                            "Add free Alpaca API keys to fetch 1-minute (intraday) bars.",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Keys are stored only on this machine, in ~/.ejournal/credentials.json. " +
                            "Get free keys at alpaca.markets.",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    OutlinedTextField(
                        value = state.keyId,
                        onValueChange = vm::updateKeyId,
                        label = { Text("Alpaca API Key ID") },
                        singleLine = true,
                        modifier = Modifier.width(420.dp),
                    )
                    OutlinedTextField(
                        value = state.secretKey,
                        onValueChange = vm::updateSecretKey,
                        label = { Text("Alpaca Secret Key") },
                        singleLine = true,
                        visualTransformation = if (state.hasSavedKeys && state.secretKey.isNotEmpty())
                            PasswordVisualTransformation() else VisualTransformation.None,
                        modifier = Modifier.width(420.dp),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        AppPrimaryButton(
                            text = "Save",
                            onClick = vm::save,
                            enabled = state.keyId.isNotBlank() && state.secretKey.isNotBlank(),
                        )
                        AppSecondaryButton(
                            text = if (state.testing) "Testing…" else "Test Connection",
                            onClick = vm::testConnection,
                            enabled = !state.testing && state.hasSavedKeys,
                        )
                        if (state.justSaved) {
                            Text("Saved", color = AppTheme.colors.profit, style = MaterialTheme.typography.bodySmall)
                        }
                        state.connectionResult?.let { ConnectionResultText(it) }
                    }
                }
            }

            AppCard {
                SectionTitle("Trade Zero API")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "Connect your Trade Zero account to import historical orders directly from the broker.",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Keys are stored only on this machine, in ~/.ejournal/credentials.json.",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = state.tradeZeroKeyId,
                        onValueChange = vm::updateTradeZeroKeyId,
                        label = { Text("API Key ID") },
                        singleLine = true,
                        modifier = Modifier.width(420.dp),
                    )
                    OutlinedTextField(
                        value = state.tradeZeroSecretKey,
                        onValueChange = vm::updateTradeZeroSecretKey,
                        label = { Text("API Secret Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.width(420.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        AppPrimaryButton(
                            text = "Save",
                            onClick = vm::saveTradeZero,
                            enabled = state.tradeZeroKeyId.isNotBlank() && state.tradeZeroSecretKey.isNotBlank(),
                        )
                        AppSecondaryButton(
                            text = if (state.tradeZeroTesting) "Testing…" else "Test Connection",
                            onClick = vm::testTradeZeroConnection,
                            enabled = !state.tradeZeroTesting && state.hasSavedTradeZeroCredentials,
                        )
                        if (state.tradeZeroJustSaved && state.tradeZeroConnectionResult == null) {
                            Text("Saved", color = AppTheme.colors.profit, style = MaterialTheme.typography.bodySmall)
                        }
                        state.tradeZeroConnectionResult?.let { ConnectionResultText(it) }
                    }
                }
            }

            AppCard {
                SectionTitle("Sync")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "Market data syncs automatically after imports and on startup. " +
                            "Run it manually after adding keys to backfill older trades.",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        AppSecondaryButton(
                            text = "Sync market data",
                            onClick = { marketDataService.requestSync() },
                        )
                        MarketDataSyncStatus(status = syncStatus, onRetry = { marketDataService.requestSync() })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = AppTheme.colors.textPrimary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = Spacing.md),
    )
}

@Composable
private fun ConnectionResultText(result: ConnectionResult) {
    val (text, color) = when (result) {
        is ConnectionResult.Connected -> "✓ Connected" to AppTheme.colors.profit
        is ConnectionResult.InvalidKeys -> "✗ Invalid keys" to AppTheme.colors.loss
        is ConnectionResult.NetworkError -> "✗ Network error" to AppTheme.colors.loss
    }
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)
}

/** System / Light / Dark segmented control, same pattern as [io.earlisreal.ejournal.ui.components.SegmentToggle]. */
@Composable
private fun ThemeModeToggle(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
) {
    Row(modifier = Modifier.clip(PillShape).background(AppTheme.colors.surfaceElevated)) {
        ThemeMode.entries.forEach { option ->
            val active = option == mode
            Text(
                text = when (option) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                color = if (active) AppTheme.colors.onAccent else AppTheme.colors.textMuted,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) AppTheme.colors.accent else Color.Transparent)
                    .clickable { onModeChange(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
