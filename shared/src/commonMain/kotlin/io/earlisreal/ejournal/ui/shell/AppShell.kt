package io.earlisreal.ejournal.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.data.repository.PortfolioFilterPrefs
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.data.repository.loadPortfolioFilterPrefs
import io.earlisreal.ejournal.data.repository.normalizePortfolioFilterPrefs
import io.earlisreal.ejournal.data.repository.putPortfolioFilterPrefs
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.TagMatch
import io.earlisreal.ejournal.domain.analytics.resolveRange
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.moomoo.MoomooClient
import io.earlisreal.ejournal.domain.alpaca.AlpacaBrokerClient
import io.earlisreal.ejournal.domain.tradezero.TradeZeroClient
import io.earlisreal.ejournal.domain.update.UpdateManager
import io.earlisreal.ejournal.ui.components.UpdateBanner
import io.earlisreal.ejournal.ui.components.PortfolioManagerDialog
import io.earlisreal.ejournal.ui.components.StatusBar
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.ThemeMode
import io.earlisreal.ejournal.ui.theme.resolveDarkMode
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Shell hand-off for screens: analysis navigation + theme state owned by the shell. */
data class ShellNav(
    val analysisPositions: List<ClosedPosition>,
    val analysisIndex: Int,
    val analysisPortfolioId: Long?,
    val analysisPortfolioName: String?,
    val onAnalyze: (ClosedPosition, List<ClosedPosition>) -> Unit,
    val onNavigate: (Destination) -> Unit,
    /** Set the active portfolio's tag filter to a single tag and jump to Trade Logs. */
    val onSelectTag: (Long) -> Unit,
    val onTagDeleted: (Long) -> Unit,
    val themeMode: ThemeMode,
    val onThemeChange: (ThemeMode) -> Unit,
    val analysisSource: Destination?,
    val onBackFromAnalysis: (() -> Unit)?,
)

internal fun hasPortfolioChanged(previousPortfolioId: Long?, nextPortfolioId: Long?): Boolean =
    previousPortfolioId != nextPortfolioId

internal fun portfolioAfterReload(portfolios: List<Portfolio>, activePortfolioId: Long?): Portfolio? =
    portfolios.firstOrNull { it.id == activePortfolioId } ?: portfolios.firstOrNull()

internal fun refreshPortfolioMetadata(
    active: ActivePortfolioFilters,
    next: Portfolio?,
): ActivePortfolioFilters? =
    if (hasPortfolioChanged(active.portfolio?.id, next?.id)) null else active.copy(portfolio = next)

internal fun selectedTagsAfterTagDeletion(selectedTagIds: Set<Long>, deletedTagId: Long): Set<Long> =
    selectedTagIds - deletedTagId

internal data class ActivePortfolioFilters(
    val portfolio: Portfolio?,
    val filters: PortfolioFilterPrefs,
)

internal suspend fun restoreActivePortfolioFilters(
    next: Portfolio?,
    portfolioSettings: PortfolioSettingsRepository,
    settingsRepository: SettingsRepository,
    tagRepository: TagRepository,
): ActivePortfolioFilters {
    val filters = next?.let { portfolioSettings.loadPortfolioFilterPrefs(it.id, tagRepository) }
        ?: PortfolioFilterPrefs()
    settingsRepository.setSelectedPortfolioId(next?.id)
    return ActivePortfolioFilters(next, filters)
}

@Composable
fun AppShell(
    portfolioRepository: PortfolioRepository,
    transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository,
    portfolioSettings: PortfolioSettingsRepository,
    credentialsRepository: CredentialsRepository,
    alpacaBrokerClient: AlpacaBrokerClient,
    tradeZeroClient: TradeZeroClient,
    moomooClient: MoomooClient,
    tagRepository: TagRepository,
    backgroundTaskTracker: BackgroundTaskTracker,
    initialDestination: Destination,
    initialPortfolios: List<Portfolio>,
    initialSelectedPortfolioId: Long?,
    initialFilterPrefs: PortfolioFilterPrefs,
    updateManager: UpdateManager? = null,
    content: @Composable (Destination, FilterState, ShellNav) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val filterMutex = remember { Mutex() }

    var current by remember { mutableStateOf(initialDestination) }
    var userExpanded by remember { mutableStateOf(true) }
    var themeMode by remember { mutableStateOf(settingsRepository.getThemeMode()) }
    var analysisPositions by remember { mutableStateOf<List<ClosedPosition>>(emptyList()) }
    var analysisIndex by remember { mutableStateOf(0) }
    var analysisPortfolioId by remember { mutableStateOf<Long?>(null) }
    var analysisPortfolioName by remember { mutableStateOf<String?>(null) }
    var analysisSource by remember { mutableStateOf<Destination?>(null) }
    var showPortfolioManager by remember { mutableStateOf(false) }

    var portfolios by remember { mutableStateOf(initialPortfolios) }
    var activeFilters by remember {
        mutableStateOf(
            ActivePortfolioFilters(
                portfolio = initialSelectedPortfolioId?.let { id -> initialPortfolios.firstOrNull { it.id == id } },
                filters = initialFilterPrefs,
            )
        )
    }
    var switchingPortfolio by remember { mutableStateOf(false) }
    // (removed: LaunchedEffect that loaded portfolios and switched to DASHBOARD — now resolved
    //  behind the splash by resolveStartDestination / buildReadyApp)

    fun clearAnalysis() {
        analysisPositions = emptyList()
        analysisIndex = 0
        analysisPortfolioId = null
        analysisPortfolioName = null
        analysisSource = null
    }

    fun updateFilters(transform: (PortfolioFilterPrefs) -> PortfolioFilterPrefs, after: () -> Unit = {}) {
        if (switchingPortfolio) return
        scope.launch {
            filterMutex.withLock {
                if (switchingPortfolio) return@withLock
                val active = activeFilters
                val next = normalizePortfolioFilterPrefs(transform(active.filters))
                if (next == active.filters) {
                    after()
                    return@withLock
                }
                active.portfolio?.let { portfolioSettings.putPortfolioFilterPrefs(it.id, next) }
                activeFilters = active.copy(filters = next)
                after()
            }
        }
    }

    fun selectPortfolio(next: Portfolio?) {
        if (switchingPortfolio || !hasPortfolioChanged(activeFilters.portfolio?.id, next?.id)) return
        switchingPortfolio = true
        scope.launch {
            try {
                filterMutex.withLock {
                    val previous = activeFilters
                    if (!hasPortfolioChanged(previous.portfolio?.id, next?.id)) {
                        activeFilters = previous.copy(portfolio = next)
                        return@withLock
                    }
                    val restored = restoreActivePortfolioFilters(
                        next = next,
                        portfolioSettings = portfolioSettings,
                        settingsRepository = settingsRepository,
                        tagRepository = tagRepository,
                    )
                    clearAnalysis()
                    activeFilters = restored
                }
            } finally {
                switchingPortfolio = false
            }
        }
    }

    fun reloadPortfolios() {
        scope.launch {
            val list = portfolioRepository.getAll()
            portfolios = list
            val currentPortfolio = activeFilters.portfolio
            val next = portfolioAfterReload(list, currentPortfolio?.id)
            if (hasPortfolioChanged(currentPortfolio?.id, next?.id)) {
                selectPortfolio(next)
            } else {
                filterMutex.withLock {
                    refreshPortfolioMetadata(activeFilters, next)?.let { activeFilters = it }
                }
            }
        }
    }

    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val activePrefs = activeFilters.filters
    val customRange = activePrefs.customFrom?.let { from -> activePrefs.customTo?.let { to -> DateRange(from, to) } }
    val filterState = FilterState(
        portfolio = activeFilters.portfolio,
        dateRange = resolveRange(activePrefs.preset, today, customRange),
        segment = activePrefs.segment,
        selectedTagIds = activePrefs.selectedTagIds,
        tagMatch = activePrefs.tagMatch,
    )

    val systemDark = isSystemInDarkTheme()
    AppTheme(darkTheme = resolveDarkMode(themeMode, systemDark)) {
        val backgroundTasks by backgroundTaskTracker.tasks.collectAsState()
        val updateState = updateManager?.state?.collectAsState()?.value
        Column(modifier = Modifier.fillMaxSize()) {
            updateState?.let { UpdateBanner(it, updateManager) }
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f).background(AppTheme.colors.contentBackground),
            ) {
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
                            selectedPortfolio = activeFilters.portfolio,
                            enabled = !switchingPortfolio,
                            onSelectPortfolio = { next ->
                                if (hasPortfolioChanged(activeFilters.portfolio?.id, next.id)) selectPortfolio(next)
                            },
                            preset = activePrefs.preset,
                            customRange = customRange,
                            onDateChange = { p, r -> updateFilters(transform = { it.copy(preset = p, customFrom = r?.from, customTo = r?.to) }) },
                            segment = activePrefs.segment,
                            onSegmentChange = { value -> updateFilters(transform = { it.copy(segment = value) }) },
                            showDateFilter = current != Destination.CALENDAR,
                            onManagePortfolios = { showPortfolioManager = true },
                            tagRepository = tagRepository,
                            selectedTagIds = activePrefs.selectedTagIds,
                            tagMatch = activePrefs.tagMatch,
                            onToggleTagFilter = { id -> updateFilters(transform = { prefs ->
                                prefs.copy(selectedTagIds = if (id in prefs.selectedTagIds) prefs.selectedTagIds - id else prefs.selectedTagIds + id)
                            }) },
                            onSetTagMatch = { value -> updateFilters(transform = { it.copy(tagMatch = value) }) },
                            onClearTagFilter = { updateFilters(transform = { it.copy(selectedTagIds = emptySet()) }) },
                            showTagFilter = current in setOf(Destination.DASHBOARD, Destination.TRADE_LOGS, Destination.CALENDAR),
                        )
                        content(
                            current,
                            filterState,
                            ShellNav(
                                analysisPositions = analysisPositions,
                                analysisIndex     = analysisIndex,
                                onAnalyze = { position, list ->
                                    analysisSource    = current
                                    analysisPortfolioId = activeFilters.portfolio?.id
                                    analysisPortfolioName = activeFilters.portfolio?.name
                                    analysisPositions = list
                                    analysisIndex     = list.indexOf(position).coerceAtLeast(0)
                                    current = Destination.ANALYSIS
                                },
                                onNavigate = { dest ->
                                    if (dest.enabled) { analysisSource = null; current = dest }
                                },
                                onSelectTag = { id -> updateFilters(
                                    transform = { it.copy(selectedTagIds = setOf(id), tagMatch = TagMatch.ANY) },
                                    after = { analysisSource = null; current = Destination.TRADE_LOGS },
                                ) },
                                onTagDeleted = { id ->
                                    updateFilters(transform = { prefs ->
                                        prefs.copy(selectedTagIds = selectedTagsAfterTagDeletion(prefs.selectedTagIds, id))
                                    })
                                },
                                analysisPortfolioId = analysisPortfolioId,
                                analysisPortfolioName = analysisPortfolioName,
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
            StatusBar(tasks = backgroundTasks)
        }

        if (showPortfolioManager) {
            PortfolioManagerDialog(
                portfolioRepository = portfolioRepository,
                transactionRepository = transactionRepository,
                portfolioSettings = portfolioSettings,
                credentialsRepository = credentialsRepository,
                alpacaBrokerClient = alpacaBrokerClient,
                tradeZeroClient = tradeZeroClient,
                moomooClient = moomooClient,
                onChanged = { reloadPortfolios() },
                onDismiss = { showPortfolioManager = false },
            )
        }
    }
}
