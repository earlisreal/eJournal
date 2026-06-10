package io.earlisreal.ejournal

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.parser.TransactionParser
import io.earlisreal.ejournal.ui.navigation.Screen
import io.earlisreal.ejournal.ui.screen.ImportScreen
import io.earlisreal.ejournal.ui.screen.TradeLogsScreen

@Composable
fun App(
    portfolioRepository: PortfolioRepository,
    transactionRepository: TransactionRepository,
    parsers: List<TransactionParser>,
) {
    MaterialTheme {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Import) }

        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                NavigationRailItem(
                    selected = currentScreen is Screen.Import,
                    onClick = { currentScreen = Screen.Import },
                    icon = { Text("+") },
                    label = { Text("Import") }
                )
                NavigationRailItem(
                    selected = currentScreen is Screen.TradeLogs,
                    onClick = { currentScreen = Screen.TradeLogs },
                    icon = { Text("=") },
                    label = { Text("Trade Logs") }
                )
            }

            when (currentScreen) {
                Screen.Import -> ImportScreen(
                    portfolioRepository = portfolioRepository,
                    transactionRepository = transactionRepository,
                    parsers = parsers,
                    onImportSuccess = { currentScreen = Screen.TradeLogs }
                )
                Screen.TradeLogs -> TradeLogsScreen()
            }
        }
    }
}
