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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.earlisreal.ejournal.data.repository.FilterPrefs
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.resolveRange
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.ui.components.PortfolioManagerDialog
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.ThemeMode
import io.earlisreal.ejournal.ui.theme.resolveDarkMode
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Shell hand-off for screens: analysis navigation + theme state owned by the shell. */
data class ShellNav(
    val selectedAnalysis: ClosedPosition?,
    val analysisPositions: List<ClosedPosition>,
    val analysisIndex: Int,
    val onAnalyze: (ClosedPosition, List<ClosedPosition>) -> Unit,
    val themeMode: ThemeMode,
    val onThemeChange: (ThemeMode) -> Unit,
    val analysisSource: Destination?,
    val onBackFromAnalysis: (() -> Unit)?,
)

@Composable
fun AppShell(
    portfolioRepository: PortfolioRepository,
    transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository,
    content: @Composable (Destination, FilterState, ShellNav) -> Unit,
) {
    val savedFilter = remember { settingsRepository.getFilterPrefs() }
    val scope = rememberCoroutineScope()

    var current by remember { mutableStateOf(Destination.DEFAULT) }
    var userExpanded by remember { mutableStateOf(true) }
    var themeMode by remember { mutableStateOf(settingsRepository.getThemeMode()) }
    var selectedAnalysis by remember { mutableStateOf<ClosedPosition?>(null) }
    var analysisPositions by remember { mutableStateOf<List<ClosedPosition>>(emptyList()) }
    var analysisIndex by remember { mutableStateOf(0) }
    var analysisSource by remember { mutableStateOf<Destination?>(null) }
    var showPortfolioManager by remember { mutableStateOf(false) }

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
        val portfolioId = selectedPortfolio?.id
        if (portfolioId != null && transactionRepository.countByPortfolio(portfolioId) > 0L) {
            current = Destination.DASHBOARD
        }
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

    fun reloadPortfolios() {
        scope.launch {
            val list = portfolioRepository.getAll()
            portfolios = list
            selectedPortfolio = list.firstOrNull { it.id == selectedPortfolio?.id } ?: list.firstOrNull()
            persist()
        }
    }

    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val filterState = FilterState(
        portfolio = selectedPortfolio,
        dateRange = resolveRange(preset, today, customRange),
        segment = segment,
    )

    val systemDark = isSystemInDarkTheme()
    AppTheme(darkTheme = resolveDarkMode(themeMode, systemDark)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(AppTheme.colors.contentBackground)) {
            val sidebarState = resolveSidebarState(maxWidth.value.toInt(), userExpanded)
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    state = sidebarState,
                    current = current,
                    onSelect = { dest ->
                        if (dest.enabled) {
                            analysisSource = null
                            current = dest
                        }
                    },
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
                        showDateFilter = current != Destination.CALENDAR,
                        onManagePortfolios = { showPortfolioManager = true },
                    )
                    content(
                        current,
                        filterState,
                        ShellNav(
                            selectedAnalysis  = selectedAnalysis,
                            analysisPositions = analysisPositions,
                            analysisIndex     = analysisIndex,
                            onAnalyze = { position, list ->
                                analysisSource    = current
                                selectedAnalysis  = position
                                analysisPositions = list
                                analysisIndex     = list.indexOf(position).coerceAtLeast(0)
                                current = Destination.ANALYSIS
                            },
                            themeMode    = themeMode,
                            onThemeChange = { themeMode = it; settingsRepository.setThemeMode(it) },
                            analysisSource = analysisSource,
                            onBackFromAnalysis = analysisSource?.let { src ->
                                { current = src; analysisSource = null }
                            },
                        ),
                    )
                }
            }
        }

        if (showPortfolioManager) {
            PortfolioManagerDialog(
                portfolioRepository = portfolioRepository,
                transactionRepository = transactionRepository,
                onChanged = { reloadPortfolios() },
                onDismiss = { showPortfolioManager = false },
            )
        }
    }
}
