package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.ui.components.AppCard
import io.earlisreal.ejournal.ui.components.AppPrimaryButton
import io.earlisreal.ejournal.ui.components.AppSecondaryButton
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing

@Composable
fun NetworkDisclosureScreen(
    marketDataEnabled: Boolean,
    updateChecksEnabled: Boolean,
    onMarketDataChange: (Boolean) -> Unit,
    onUpdateChecksChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onExit: () -> Unit,
) {
    AppTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppCard(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("Network and privacy", style = MaterialTheme.typography.headlineMedium, color = AppTheme.colors.textPrimary)
                    Text(
                        "eJournal is local-first. Broker synchronization and local eTape imports stay separate from online market-data requests; copying eTape data from a local file does not contact an external service.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textPrimary,
                    )
                    Text(
                        buildAnnotatedString {
                            append("When enabled, market-data requests can send ticker symbols and date ranges derived from imported trades to Yahoo Finance or Alpaca. See ")
                            withLink(
                                LinkAnnotation.Url(
                                    "https://github.com/earlisreal/eJournal/blob/main/PRIVACY.md",
                                    TextLinkStyles(style = SpanStyle(color = AppTheme.colors.accent)),
                                ),
                            ) { append("PRIVACY.md") }
                            append(" for the details.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textMuted,
                    )
                    Text(
                        buildAnnotatedString {
                            append("Provider policies: ")
                            withLink(
                                LinkAnnotation.Url(
                                    "https://legal.yahoo.com/us/en/yahoo/privacy/index.html",
                                    TextLinkStyles(style = SpanStyle(color = AppTheme.colors.accent)),
                                ),
                            ) { append("Yahoo") }
                            append(" · ")
                            withLink(
                                LinkAnnotation.Url(
                                    "https://alpaca.markets/disclosures",
                                    TextLinkStyles(style = SpanStyle(color = AppTheme.colors.accent)),
                                ),
                            ) { append("Alpaca") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textMuted,
                    )
                    SettingRow(
                        title = "Allow automatic online market data",
                        description = "Off by default. You can enable it later in Settings.",
                        checked = marketDataEnabled,
                        onCheckedChange = onMarketDataChange,
                    )
                    SettingRow(
                        title = "Check for updates automatically",
                        description = "Checks the public GitHub release feed with normal network metadata and an eJournal user agent; no journal, broker, portfolio, credential, or device data is sent.",
                        checked = updateChecksEnabled,
                        onCheckedChange = onUpdateChecksChange,
                    )
                    Text(
                        "You can change either choice at any time in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textMuted,
                    )
                    Text(
                        "Broker startup synchronization remains an independent per-portfolio setting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textMuted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        AppPrimaryButton(text = "Continue", onClick = onContinue)
                        AppSecondaryButton(text = "Exit", onClick = onExit)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
            Text(description, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
