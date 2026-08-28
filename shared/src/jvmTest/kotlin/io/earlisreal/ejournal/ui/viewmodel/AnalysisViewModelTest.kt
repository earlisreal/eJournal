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

    private fun position() = ClosedPosition(
        symbol = "AAPL",
        entryDatetime = LocalDateTime.parse("2026-06-10T09:30:05"),
        exitDatetime = LocalDateTime.parse("2026-06-10T10:00:05"),
        averageEntryPrice = 100.0,
        averageExitPrice = 101.0,
        shares = 10.0,
        fees = 1.0,
        profitLoss = 10.0,
        transactions = listOf(
            Transaction(1L, 1L, "AAPL", LocalDateTime.parse("2026-06-10T09:30:05"), Action.BUY, 100.0, 10.0, 0.5),
            Transaction(2L, 1L, "AAPL", LocalDateTime.parse("2026-06-10T10:00:05"), Action.SELL, 101.0, 10.0, 0.5),
        ),
        market = Market.US_STOCKS,
    )

    private fun bar(timestamp: String) = Bar(
        symbol = "AAPL",
        timeframe = Timeframe.TEN_SECONDS,
        timestamp = LocalDateTime.parse(timestamp),
        open = 100.0,
        high = 101.0,
        low = 99.0,
        close = 100.5,
        volume = 1_000L,
    )

    private class FakeMarketDataRepository(
        private val tenSecondBars: List<Bar>,
    ) : MarketDataRepository {
        data class Query(val timeframe: Timeframe, val from: LocalDateTime, val to: LocalDateTime)

        val tenSecondQueries = mutableListOf<Query>()

        override suspend fun upsertBars(market: Market, bars: List<Bar>) = Unit

        override suspend fun getCoverage(symbol: String, timeframe: Timeframe, market: Market) = null

        override suspend fun getBars(
            symbol: String,
            timeframe: Timeframe,
            market: Market,
            from: LocalDateTime,
            to: LocalDateTime,
        ): List<Bar> {
            if (timeframe != Timeframe.TEN_SECONDS) return emptyList()
            tenSecondQueries += Query(timeframe, from, to)
            return tenSecondBars
        }
    }
}
