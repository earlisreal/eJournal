package io.earlisreal.ejournal.ui.viewmodel

import io.earlisreal.ejournal.data.*
import io.earlisreal.ejournal.data.database.JvmDatabaseFactory
import io.earlisreal.ejournal.domain.*
import io.earlisreal.ejournal.domain.analytics.*
import io.earlisreal.ejournal.domain.model.*
import io.earlisreal.ejournal.ui.shell.FilterState
import kotlinx.coroutines.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.datetime.*
import java.nio.file.Files
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun cleanup() = Dispatchers.resetMain()

    private val db = JvmDatabaseFactory.create(Files.createTempDirectory("report-test").toFile())
    private val portfolios = SqlDelightPortfolioRepository(db)
    private val transactions = SqlDelightTransactionRepository(db)
    private val tags = SqlDelightTagRepository(db)
    private val closed = ClosedPositionService(transactions, portfolios)
    private fun vm() = ReportsViewModel(PositionTagService(closed, tags), tags)
    private suspend fun ReportsViewModel.ready() = withContext(Dispatchers.Default) { withTimeout(5000) { state.first { !it.loading } } }
    private fun tx(p: Long, key: String, action: Action, shares: Double, time: String) = Transaction(
        0, p, "AAPL", LocalDateTime.parse(time), action, if (action == Action.BUY) 100.0 else 102.0, shares, 0.25, key)

    @Test fun stalePortfolioLoadsCannotReplaceCurrentReport() = runTest {
        val first = portfolios.insert("First", Market.US_STOCKS)
        val second = portfolios.insert("Second", Market.US_STOCKS)
        for (p in listOf(first, second)) {
            transactions.insert(tx(p.id, "open", Action.BUY, 1.0, "2026-10-12T09:30"))
            transactions.insert(tx(p.id, "close", Action.SELL, 1.0, "2026-10-12T09:31"))
        }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val delayed = object : io.earlisreal.ejournal.data.repository.TransactionRepository by transactions {
            override suspend fun getByPortfolio(portfolioId: Long): List<Transaction> {
                if (portfolioId == first.id) withContext(NonCancellable) {
                    started.complete(Unit)
                    release.await()
                    finished.complete(Unit)
                }
                return transactions.getByPortfolio(portfolioId)
            }
        }
        val vm = ReportsViewModel(PositionTagService(ClosedPositionService(delayed, portfolios), tags), tags)
        vm.load(FilterState(first, DateRange(null, null), Segment.ALL))
        withContext(Dispatchers.Default) { withTimeout(5000) { started.await() } }
        vm.load(FilterState(second, DateRange(null, null), Segment.ALL))
        assertEquals(second.id, vm.ready().buckets.flatMap { it.positions }.single().transactions.first().portfolioId)
        release.complete(Unit)
        withContext(Dispatchers.Default) { withTimeout(5000) { finished.await() } }
        assertEquals(second.id, vm.state.value.buckets.flatMap { it.positions }.single().transactions.first().portfolioId)
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.viewModelScope.coroutineContext[Job]!!.children.toList().joinAll() } }
    }

    @Test fun exactCohortExcludesOpenPositionsAndSurvivesRefreshAndSort() = runTest {
        val p = portfolios.insert("Test", Market.US_STOCKS)
        val open = tx(p.id, "open", Action.BUY, 10.0, "2026-10-12T09:30")
        val close = tx(p.id, "close", Action.SELL, 10.0, "2026-10-12T10:30")
        transactions.insertOrEnrich(open)
        transactions.insertOrEnrich(close)
        transactions.insertOrEnrich(open.copy(externalId = "still-open", datetime = LocalDateTime.parse("2026-10-13T09:30")))
        transactions.insertOrEnrich(close.copy(externalId = "partial", shares = 5.0, datetime = LocalDateTime.parse("2026-10-13T10:30")))
        val filter = FilterState(p, DateRange(null, null), Segment.ALL)
        val vm = vm()
        vm.load(filter)
        assertEquals(1, vm.ready().total)
        vm.select("slot:0")
        assertNull(vm.state.value.selectedKey)
        vm.select("unverified")
        vm.sortBy(ReportMetric.NET_PNL)
        vm.sortPositionsBy(SortColumn.SYMBOL)
        val cohort = vm.state.value.cohort
        assertEquals(1, cohort.size)
        vm.refresh()
        val refreshed = vm.ready()
        assertEquals(cohort, refreshed.cohort)
        assertEquals("unverified", refreshed.selectedKey)
        assertEquals(ReportMetric.NET_PNL, refreshed.metric)
        assertEquals(SortColumn.SYMBOL, refreshed.positionSort)
        vm.load(filter.copy(segment = Segment.DAY))
        assertNull(vm.ready().selectedKey)
    }

    @Test fun timingOnlyRefreshRegroupsAndClearsDisappearingBucket() = runTest {
        val p = portfolios.insert("Test", Market.US_STOCKS)
        val open = tx(p.id, "open", Action.BUY, 1.0, "2026-10-12T09:30")
        val close = tx(p.id, "close", Action.SELL, 1.0, "2026-10-12T09:31")
        transactions.insertOrEnrich(open)
        transactions.insertOrEnrich(close)
        val vm = vm()
        vm.load(FilterState(p, DateRange(null, null), Segment.ALL))
        assertEquals(0, vm.ready().verified)
        vm.select("unverified")
        transactions.insertOrEnrich(open.copy(verifiedExecutionInstant = Instant.parse("2026-10-12T13:30:00Z")))
        transactions.insertOrEnrich(close.copy(verifiedExecutionInstant = Instant.parse("2026-10-12T13:31:00Z")))
        vm.refresh()
        val next = vm.ready()
        assertEquals(1, next.verified)
        assertNull(next.selectedKey)
        assertEquals(1, next.buckets.single { it.key == "slot:19" }.positions.size)
        assertEquals(60.0, next.buckets.single { it.key == "slot:19" }.positions.single().verifiedHoldSeconds())
    }

    @Test fun tagEditsRecomputeFilteredCohortAndTagComparisonKeepsException() = runTest {
        val p = portfolios.insert("Tags", Market.US_STOCKS)
        val id = transactions.insert(tx(p.id, "open", Action.BUY, 1.0, "2026-10-12T09:30"))!!
        transactions.insert(tx(p.id, "close", Action.SELL, 1.0, "2026-10-12T09:31"))
        val tagId = tags.create(p.id, "Setup", "#FF0000")
        tags.addTag(p.id, id, tagId)
        val vm = vm()
        val filter = FilterState(p, DateRange(null, null), Segment.ALL, setOf(tagId), TagMatch.ALL)
        vm.load(filter)
        assertEquals(1, vm.ready().total)
        vm.select("unverified")
        val position = vm.state.value.cohort.single()
        vm.toggleTag(position, vm.state.value.allTags.single())
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first { !it.loading && it.total == 0 } } }
        assertNull(vm.state.value.selectedKey)
        assertEquals(1, vm.state.value.stats.sumOf { it.metrics.tradeCount })
    }
}
