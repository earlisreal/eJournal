package io.earlisreal.ejournal.ui.viewmodel

import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `sub-minute Position defaults to 10-second timeframe when complete data exists`() = runTest {
        val repository = FakeMarketDataRepository(
            tenSecondBars = listOf(
                bar("2026-06-10T09:30:00"),
                bar("2026-06-10T09:30:30"),
            ),
        )
        val vm = AnalysisViewModel(repository)
        vm.init(
            listOf(position(
                entry = "2026-06-10T09:30:05",
                exit = "2026-06-10T09:30:35",
            )),
            index = 0,
            isDarkTheme = true,
        )

        vm.state.first { it.hasTenSecData && it.activeTimeframe == io.earlisreal.ejournal.domain.marketdata.ChartTimeframe.TEN_SEC && it.chartData != null && !it.loading }

        assertEquals(Timeframe.TEN_SECONDS, repository.tenSecondQueries.last().timeframe)
        assertEquals(io.earlisreal.ejournal.domain.marketdata.ChartTimeframe.TEN_SEC, vm.state.value.activeTimeframe)
    }

    @Test
    fun `sub-minute Position falls back to one-minute timeframe without complete 10-second data`() = runTest {
        val repository = FakeMarketDataRepository(
            tenSecondBars = emptyList(),
            oneMinuteBars = listOf(bar("2026-06-10T09:30:00", Timeframe.ONE_MINUTE)),
        )
        val vm = AnalysisViewModel(repository)
        vm.init(
            listOf(position(
                entry = "2026-06-10T09:30:05",
                exit = "2026-06-10T09:30:35",
            )),
            index = 0,
            isDarkTheme = true,
        )

        vm.state.first { it.activeTimeframe == io.earlisreal.ejournal.domain.marketdata.ChartTimeframe.ONE_MIN && !it.loading }

        assertFalse(vm.state.value.hasTenSecData)
        assertTrue(repository.oneMinuteQueries.isNotEmpty())
        assertTrue(vm.state.value.chartData != null)
    }

    @Test
    fun `60-second Position keeps one-minute default`() = runTest {
        val vm = AnalysisViewModel(FakeMarketDataRepository())
        vm.init(
            listOf(position(
                entry = "2026-06-10T09:30:00",
                exit = "2026-06-10T09:31:00",
            )),
            index = 0,
            isDarkTheme = true,
        )

        assertEquals(io.earlisreal.ejournal.domain.marketdata.ChartTimeframe.ONE_MIN, vm.state.value.activeTimeframe)
    }

    @Test
    fun `empty Position list clears the previous Analysis selection`() = runTest {
        val vm = AnalysisViewModel(FakeMarketDataRepository())
        vm.init(listOf(position()), index = 0, isDarkTheme = true)
        assertEquals("AAPL", vm.state.value.position?.symbol)

        vm.init(emptyList(), index = 0, isDarkTheme = false)

        assertEquals(null, vm.state.value.position)
        assertEquals(0, vm.state.value.totalCount)
        assertFalse(vm.state.value.loading)
        assertFalse(vm.state.value.isDarkTheme)
    }

    @Test
    fun `10-second availability covers every fill and loads the entire Position date`() = runTest {
        val repository = FakeMarketDataRepository(
            tenSecondBars = listOf(
                bar("2026-06-10T09:30"),
                bar("2026-06-10T10:00"),
            ),
        )
        val vm = AnalysisViewModel(repository)
        vm.init(listOf(position()), index = 0, isDarkTheme = true)

        vm.state.first { it.hasTenSecData }
        vm.selectTimeframe(io.earlisreal.ejournal.domain.marketdata.ChartTimeframe.TEN_SEC)
        vm.state.first { it.chartData != null }

        assertEquals(Timeframe.TEN_SECONDS, repository.tenSecondQueries.last().timeframe)
        assertEquals(LocalDateTime.parse("2026-06-10T00:00"), repository.tenSecondQueries.last().from)
        assertEquals(LocalDateTime.parse("2026-06-10T23:59:59"), repository.tenSecondQueries.last().to)
        assertEquals(2, vm.state.value.chartData!!.bars.size)
    }

    @Test
    fun `10-second timeframe stays disabled when a fill bucket is missing`() = runTest {
        val repository = FakeMarketDataRepository(tenSecondBars = listOf(bar("2026-06-10T09:30")))
        val vm = AnalysisViewModel(repository)
        vm.init(listOf(position()), index = 0, isDarkTheme = true)

        vm.state.first { !it.hasTenSecData && !it.loading }
        vm.selectTimeframe(io.earlisreal.ejournal.domain.marketdata.ChartTimeframe.TEN_SEC)

        assertFalse(vm.state.value.hasTenSecData)
        assertTrue(vm.state.value.activeTimeframe != io.earlisreal.ejournal.domain.marketdata.ChartTimeframe.TEN_SEC)
    }

    private fun position(
        entry: String = "2026-06-10T09:30:05",
        exit: String = "2026-06-10T10:00:05",
    ) = ClosedPosition(
        symbol = "AAPL",
        entryDatetime = LocalDateTime.parse(entry),
        exitDatetime = LocalDateTime.parse(exit),
        averageEntryPrice = 100.0,
        averageExitPrice = 101.0,
        shares = 10.0,
        fees = 1.0,
        profitLoss = 10.0,
        transactions = listOf(
            Transaction(1L, 1L, "AAPL", LocalDateTime.parse(entry), Action.BUY, 100.0, 10.0, 0.5),
            Transaction(2L, 1L, "AAPL", LocalDateTime.parse(exit), Action.SELL, 101.0, 10.0, 0.5),
        ),
        market = Market.US_STOCKS,
    )

    private fun bar(timestamp: String, timeframe: Timeframe = Timeframe.TEN_SECONDS) = Bar(
        symbol = "AAPL",
        timeframe = timeframe,
        timestamp = LocalDateTime.parse(timestamp),
        open = 100.0,
        high = 101.0,
        low = 99.0,
        close = 100.5,
        volume = 1_000L,
    )

    private class FakeMarketDataRepository(
        private val tenSecondBars: List<Bar> = emptyList(),
        private val oneMinuteBars: List<Bar> = emptyList(),
    ) : MarketDataRepository {
        data class Query(val timeframe: Timeframe, val from: LocalDateTime, val to: LocalDateTime)

        val tenSecondQueries = mutableListOf<Query>()
        val oneMinuteQueries = mutableListOf<Query>()

        override suspend fun upsertBars(market: Market, bars: List<Bar>) = Unit

        override suspend fun getCoverage(symbol: String, timeframe: Timeframe, market: Market) = null

        override suspend fun getBars(
            symbol: String,
            timeframe: Timeframe,
            market: Market,
            from: LocalDateTime,
            to: LocalDateTime,
        ): List<Bar> {
            when (timeframe) {
                Timeframe.TEN_SECONDS -> {
                    tenSecondQueries += Query(timeframe, from, to)
                    return tenSecondBars
                }
                Timeframe.ONE_MINUTE -> {
                    oneMinuteQueries += Query(timeframe, from, to)
                    return oneMinuteBars
                }
                Timeframe.DAILY -> return emptyList()
            }
        }
    }
}
