package io.earlisreal.ejournal.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.*
import io.earlisreal.ejournal.data.database.JvmDatabaseFactory
import io.earlisreal.ejournal.domain.*
import io.earlisreal.ejournal.domain.analytics.*
import io.earlisreal.ejournal.domain.model.*
import io.earlisreal.ejournal.ui.screen.ReportsContent
import io.earlisreal.ejournal.ui.shell.FilterState
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.datetime.*
import java.nio.file.Files
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class ReportsUiTest {
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun cleanup() = Dispatchers.resetMain()

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }
    private fun ImageComposeScene.nodes() = semanticsOwners.flatMap { it.rootSemanticsNode.descendants() }
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    @Test fun chartAndTableOpenExactCohortsAndTabsKeepWorking() = runTest {
        val db = JvmDatabaseFactory.create(Files.createTempDirectory("reports-ui").toFile())
        val portfolios = SqlDelightPortfolioRepository(db)
        val transactions = SqlDelightTransactionRepository(db)
        val tags = SqlDelightTagRepository(db)
        val portfolio = portfolios.insert("UI test", Market.US_STOCKS)
        for ((index, minute) in listOf(30, 45).withIndex()) {
            val entry = LocalDateTime(2026, 10, 12, 9, minute)
            val exit = LocalDateTime(2026, 10, 12, 10, minute)
            val open = Transaction(0, portfolio.id, if (index == 0) "AAPL" else "NVDA", entry, Action.BUY, 100.0, 1.0, .1, "open-$index", verifiedExecutionInstant = unambiguousEasternInstant(entry))
            transactions.insertOrEnrich(open)
            transactions.insertOrEnrich(open.copy(datetime = exit, action = Action.SELL, price = 102.0, externalId = "close-$index", verifiedExecutionInstant = unambiguousEasternInstant(exit)))
        }
        val vm = ReportsViewModel(PositionTagService(ClosedPositionService(transactions, portfolios), tags), tags)
        val filter = FilterState(portfolio, DateRange(null, null), Segment.ALL)
        vm.load(filter)
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first { !it.loading } } }
        val scene = ImageComposeScene(width = 1200, height = 850, coroutineContext = coroutineContext) {
            val state by vm.state.collectAsState()
            AppTheme(darkTheme = true) { Surface { ReportsContent(state, filter, vm) } }
        }
        fun frame() { runCurrent(); scene.render(); runCurrent(); scene.render() }
        try {
            frame()
            val chart = scene.nodes().single { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text -> text.startsWith("Net P&L and Position count") } == true }
            repeat(20) { assertTrue(chart.config[SemanticsActions.CustomActions].single { it.label == "Next group" }.action()) }
            frame()
            val inspected = scene.nodes().single { it.id == chart.id }
            assertTrue(inspected.config[SemanticsProperties.StateDescription].startsWith("09:30"), inspected.config[SemanticsProperties.StateDescription])
            assertTrue(inspected.config[SemanticsActions.OnClick].action!!.invoke())
            frame()
            assertEquals("slot:19", vm.state.value.selectedKey)
            assertEquals(2, vm.state.value.cohort.size)
            assertEquals(3.6, vm.state.value.selectedBucket!!.metrics.netPnl, 1e-9)
            scene.nodes().single { it.text() == "All groups" }.config[SemanticsActions.OnClick].action!!.invoke()
            frame()
            assertNull(vm.state.value.selectedKey)
            scene.nodes().single { it.text() == "Net P&L" && it.config.getOrNull(SemanticsActions.OnClick) != null }
                .config[SemanticsActions.OnClick].action!!.invoke()
            frame()
            assertEquals(ReportMetric.NET_PNL, vm.state.value.metric)
            assertEquals(0, vm.bucketListState.firstVisibleItemIndex)
            scene.nodes().single { it.config.getOrNull(SemanticsActions.OnClick)?.label == "View 09:30 Positions" }
                .config[SemanticsActions.OnClick].action!!.invoke()
            frame()
            assertEquals(2, vm.state.value.cohort.size)
            scene.nodes().single { it.text() == "Symbol" && it.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Tab }
                .config[SemanticsActions.OnClick].action!!.invoke()
            frame()
            assertEquals(ReportGrouping.SYMBOL, vm.state.value.grouping)
            assertNull(vm.state.value.selectedKey)
            assertEquals(2, vm.state.value.total)
            transactions.deleteByPortfolio(portfolio.id)
            vm.groupBy(ReportGrouping.ENTRY_WEEKDAY)
            vm.refresh()
            withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first { !it.loading } } }
            frame()
            assertEquals(0, vm.state.value.total)
            assertTrue(scene.nodes().any { it.text().startsWith("Monday") })
            assertEquals(7, vm.state.value.buckets.size)
        } finally {
            scene.close()
            vm.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
