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
import io.earlisreal.ejournal.data.repository.FilterPrefs
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.resolveRange
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.resolveDarkMode
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@Composable
fun AppShell(
    portfolioRepository: PortfolioRepository,
    settingsRepository: SettingsRepository,
    content: @Composable (Destination, FilterState) -> Unit,
) {
    val savedFilter = remember { settingsRepository.getFilterPrefs() }

    var current by remember { mutableStateOf(Destination.DEFAULT) }
    var userExpanded by remember { mutableStateOf(true) }
    var themeMode by remember { mutableStateOf(settingsRepository.getThemeMode()) }

    var preset by remember { mutableStateOf(savedFilter?.preset ?: DateRangePreset.ALL_TIME) }
    var customRange by remember {
        mutableStateOf(savedFilter?.let { p -> p.customFrom?.let { f -> p.customTo?.let { t -> DateRange(f, t) } } })
    }
    var segment by remember { mutableStateOf(savedFilter?.segment ?: Segment.ALL) }

    var portfolios by remember { mutableStateOf<List<Portfolio>>(emptyList()) }
    var selectedPortfolio by remember { mutableStateOf<Portfolio?>(null) }
    LaunchedEffect(Unit) {
        portfolios = portfolioRepository.getAll()
        selectedPortfolio = savedFilter?.portfolioId?.let { id -> portfolios.firstOrNull { it.id == id } }
            ?: portfolios.firstOrNull()
    }

    fun persist() {
        settingsRepository.setFilterPrefs(
            FilterPrefs(
                portfolioId = selectedPortfolio?.id,
                preset = preset,
                customFrom = customRange?.from,
                customTo = customRange?.to,
                segment = segment,
            )
        )
    }

    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val filterState = FilterState(
        portfolio = selectedPortfolio,
        dateRange = resolveRange(preset, today, customRange),
        segment = segment,
    )

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
                        onSelectPortfolio = { selectedPortfolio = it; persist() },
                        preset = preset,
                        customRange = customRange,
                        onDateChange = { p, r -> preset = p; customRange = r; persist() },
                        segment = segment,
                        onSegmentChange = { segment = it; persist() },
                        themeMode = themeMode,
                        onThemeChange = { themeMode = it; settingsRepository.setThemeMode(it) },
                    )
                    content(current, filterState)
                }
            }
        }
    }
}
