package io.earlisreal.ejournal.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.ThemeMode
import io.earlisreal.ejournal.ui.theme.resolveDarkMode

@Composable
fun AppShell(
    portfolioRepository: PortfolioRepository,
    settingsRepository: SettingsRepository,
    content: @Composable (Destination, Portfolio?) -> Unit,
) {
    var current by remember { mutableStateOf(Destination.DEFAULT) }
    var userExpanded by remember { mutableStateOf(true) }
    var themeMode by remember { mutableStateOf(settingsRepository.getThemeMode()) }

    var portfolios by remember { mutableStateOf<List<Portfolio>>(emptyList()) }
    var selectedPortfolio by remember { mutableStateOf<Portfolio?>(null) }
    LaunchedEffect(Unit) {
        portfolios = portfolioRepository.getAll()
        selectedPortfolio = portfolios.firstOrNull()
    }

    val systemDark = isSystemInDarkTheme()
    AppTheme(darkTheme = resolveDarkMode(themeMode, systemDark)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(AppTheme.colors.background)) {
            val sidebarState = resolveSidebarState(maxWidth.value.toInt(), userExpanded)
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    state = sidebarState,
                    current = current,
                    onSelect = { if (it.enabled) current = it },
                    onToggle = { userExpanded = !userExpanded },
                )
                Column(modifier = Modifier.weight(1f)) {
                    TopBar(
                        portfolios = portfolios,
                        selectedPortfolio = selectedPortfolio,
                        onSelectPortfolio = { selectedPortfolio = it },
                        themeMode = themeMode,
                        onThemeChange = {
                            themeMode = it
                            settingsRepository.setThemeMode(it)
                        },
                    )
                    content(current, selectedPortfolio)
                }
            }
        }
    }
}
