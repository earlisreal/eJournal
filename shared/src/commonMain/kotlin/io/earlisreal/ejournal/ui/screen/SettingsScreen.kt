package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.domain.marketdata.AlpacaProvider
import io.earlisreal.ejournal.domain.marketdata.ConnectionResult
import io.earlisreal.ejournal.domain.marketdata.MarketDataService
import io.earlisreal.ejournal.domain.update.UpdateManager
import io.earlisreal.ejournal.domain.update.UpdateResult
import io.earlisreal.ejournal.ui.components.AppCard
import io.earlisreal.ejournal.ui.components.AppPrimaryButton
import io.earlisreal.ejournal.ui.components.AppSecondaryButton
import io.earlisreal.ejournal.ui.components.MarketDataSyncStatus
import io.earlisreal.ejournal.ui.components.ScreenScaffold
import io.earlisreal.ejournal.ui.components.SecretKeyTextField
import io.earlisreal.ejournal.ui.platform.pickEtapeDatabaseFile
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.PillShape
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.theme.ThemeMode
import io.earlisreal.ejournal.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    credentialsRepository: CredentialsRepository,
    alpacaProvider: AlpacaProvider,
    marketDataService: MarketDataService,
    settingsRepository: SettingsRepository,
    updateManager: UpdateManager? = null,
) {
    val vm = viewModel { SettingsViewModel(credentialsRepository, alpacaProvider) }
    val state by vm.state.collectAsState()
    val syncStatus by marketDataService.status.collectAsState()
    val onlineMarketDataEnabled by marketDataService.onlineMarketDataEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    var etapePath by remember { mutableStateOf(settingsRepository.getEtapeDbPath().orEmpty()) }
    var automaticUpdates by remember { mutableStateOf(settingsRepository.getAutomaticUpdateChecksEnabled()) }
    var showOnlineDataConfirmation by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Settings") {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).widthIn(max = 720.dp),
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Appearance")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("Theme", color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    ThemeModeToggle(themeMode, onThemeChange)
                }
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Alpaca Market Data")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "These credentials are used only for Alpaca market data. Broker accounts are configured per portfolio in Manage Portfolios.",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val alpacaGuideUrl = "https://alpaca.markets/learn/connect-to-alpaca-api"
                    val linkColor = AppTheme.colors.accent
                    Text(
                        text = buildAnnotatedString {
                            append("To get free keys, follow steps 1 and 2 of Alpaca's guide: ")
                            withLink(
                                LinkAnnotation.Url(
                                    alpacaGuideUrl,
                                    TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                                ),
                            ) { append(alpacaGuideUrl) }
                        },
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Keys are stored only on this machine, in ~/.ejournal/credentials.json.",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = state.keyId,
                        onValueChange = vm::updateKeyId,
                        label = { Text("API Key ID") },
                        singleLine = true,
                        modifier = Modifier.width(420.dp),
                    )
                    SecretKeyTextField(
                        value = state.secretKey,
                        onValueChange = vm::updateSecretKey,
                        label = "Alpaca Secret Key",
                        modifier = Modifier.width(420.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        AppPrimaryButton(
                            text = "Save",
                            onClick = vm::save,
                            enabled = state.keyId.isNotBlank() && state.secretKey.isNotBlank(),
                        )
                        AppSecondaryButton(
                            text = if (state.testing) "Testing…" else "Test Market Data",
                            onClick = vm::testConnection,
                            enabled = !state.testing && state.hasSavedKeys,
                        )
                        if (state.justSaved) Text("Saved", color = AppTheme.colors.profit, style = MaterialTheme.typography.bodySmall)
                        state.connectionResult?.let { ConnectionResultText(it) }
                    }
                }
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("Sync")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "Online market-data requests are off by default. Enable automatic requests here, or use the one-shot button below to confirm a request.",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = buildAnnotatedString {
                            append("When enabled, Yahoo Finance or Alpaca receive ticker symbols and requested date ranges derived from imported transactions. See ")
                            withLink(
                                LinkAnnotation.Url(
                                    "https://github.com/earlisreal/eJournal/blob/main/PRIVACY.md",
                                    TextLinkStyles(style = SpanStyle(color = AppTheme.colors.accent, textDecoration = TextDecoration.Underline)),
                                ),
                            ) { append("PRIVACY.md") }
                            append(" and the ")
                            withLink(
                                LinkAnnotation.Url(
                                    "https://legal.yahoo.com/us/en/yahoo/privacy/index.html",
                                    TextLinkStyles(style = SpanStyle(color = AppTheme.colors.accent, textDecoration = TextDecoration.Underline)),
                                ),
                            ) { append("Yahoo") }
                            append(" and ")
                            withLink(
                                LinkAnnotation.Url(
                                    "https://alpaca.markets/disclosures",
                                    TextLinkStyles(style = SpanStyle(color = AppTheme.colors.accent, textDecoration = TextDecoration.Underline)),
                                ),
                            ) { append("Alpaca") }
                            append(" policies.")
                        },
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text("Allow automatic online market data", color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = onlineMarketDataEnabled,
                            onCheckedChange = marketDataService::setOnlineMarketDataEnabled,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        AppSecondaryButton(
                            text = "Fetch online data once",
                            onClick = {
                                if (onlineMarketDataEnabled) marketDataService.requestConfirmedSync()
                                else showOnlineDataConfirmation = true
                            },
                        )
                        MarketDataSyncStatus(status = syncStatus, onRetry = { marketDataService.requestSync() })
                    }
                    Text(
                        "eTape 10-second bars are copied for US stock day Positions. The default database is ~/.eTape/etape.db.",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        AppSecondaryButton(
                            text = "Choose eTape database…",
                            onClick = {
                                scope.launch {
                                    pickEtapeDatabaseFile()?.let { path ->
                                        etapePath = path
                                        settingsRepository.setEtapeDbPath(path)
                                        marketDataService.requestSync()
                                    }
                                }
                            },
                        )
                        if (etapePath.isNotBlank()) {
                            AppSecondaryButton(
                                text = "Use default",
                                onClick = {
                                    etapePath = ""
                                    settingsRepository.setEtapeDbPath(null)
                                    marketDataService.requestSync()
                                },
                            )
                        }
                    }
                    Text(
                        if (etapePath.isBlank()) "Using default eTape path" else "Using ${etapePath}",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            updateManager?.let { manager ->
                val updateState by manager.state.collectAsState()
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("Updates")
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text(
                            "Current version: ${manager.identity.version} (${manager.identity.distribution})",
                            color = AppTheme.colors.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Checks send normal network metadata and an eJournal user agent to the public GitHub Releases API, but never journal, broker, portfolio, credential, or device data.",
                            color = AppTheme.colors.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Text("Check automatically", color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = automaticUpdates,
                                onCheckedChange = {
                                    automaticUpdates = it
                                    settingsRepository.setAutomaticUpdateChecksEnabled(it)
                                },
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            AppSecondaryButton(text = "Check now", onClick = manager::requestManualCheck)
                            when (val result = updateState.result) {
                                UpdateResult.Idle -> Text("Not checked yet", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                                UpdateResult.Checking -> Text("Checking…", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                                UpdateResult.Current -> Text("You are up to date", color = AppTheme.colors.profit, style = MaterialTheme.typography.bodySmall)
                                is UpdateResult.Available -> Text("${result.update.version} available", color = AppTheme.colors.accent, style = MaterialTheme.typography.bodySmall)
                                is UpdateResult.Failed -> Text(result.message, color = AppTheme.colors.loss, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        updateState.lastCheckedEpochMillis?.let {
                            Text(
                                "Last checked: ${kotlin.time.Instant.fromEpochMilliseconds(it)}",
                                color = AppTheme.colors.textMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOnlineDataConfirmation) {
        AlertDialog(
            onDismissRequest = { showOnlineDataConfirmation = false },
            title = { Text("Fetch online market data?") },
            text = {
                Text(
                    "This one-time request may send ticker symbols and requested date ranges derived from imported transactions to Yahoo Finance or Alpaca. It does not enable automatic requests.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOnlineDataConfirmation = false
                        marketDataService.requestConfirmedSync()
                    },
                ) { Text("Fetch once") }
            },
            dismissButton = {
                TextButton(onClick = { showOnlineDataConfirmation = false }) { Text("Cancel") }
            },
        )
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

@Composable
private fun ThemeModeToggle(mode: ThemeMode, onModeChange: (ThemeMode) -> Unit) {
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
