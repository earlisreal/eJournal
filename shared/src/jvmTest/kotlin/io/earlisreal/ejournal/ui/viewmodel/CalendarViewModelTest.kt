package io.earlisreal.ejournal.ui.viewmodel

import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.ClosedPositionService
import io.earlisreal.ejournal.domain.PositionTagService
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Tag
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.testutil.FakePortfolioRepository
import io.earlisreal.ejournal.testutil.tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import io.earlisreal.ejournal.data.repository.TransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun pos(year: Int, month: Int, day: Int, pnl: Double = 0.0): ClosedPosition {
        val dt = LocalDateTime(year, month, day, 15, 0)
        return ClosedPosition(
            symbol = "AAPL",
            entryDatetime = dt,
            exitDatetime = dt,
            averageEntryPrice = 100.0,
            averageExitPrice = 110.0,
            shares = 10.0,
            fees = 1.0,
            profitLoss = pnl,
        )
    }

    /** ClosedPositionService backed by a per-portfolio marker tx + a compute that maps it to fixtures. */
    private fun serviceWith(positions: Map<Long, List<ClosedPosition>>): ClosedPositionService {
        val repo = object : TransactionRepository {
            override suspend fun getByPortfolio(portfolioId: Long): List<Transaction> =
                listOf(tx(externalId = "p$portfolioId"))
            override suspend fun getByPortfolioAndDateRange(portfolioId: Long, from: LocalDateTime, to: LocalDateTime): List<Transaction> = emptyList()
            override suspend fun insert(transaction: Transaction): Long? = null
            override suspend fun delete(id: Long) {}
            override suspend fun countByPortfolio(portfolioId: Long): Long = 0
            override suspend fun deleteByPortfolio(portfolioId: Long) {}
        }
        val compute: (List<Transaction>) -> List<ClosedPosition> = { txs ->
            val pid = txs.first().externalId!!.removePrefix("p").toLong()
            positions[pid] ?: emptyList()
        }
        return ClosedPositionService(repo, FakePortfolioRepository(), compute)
    }

    private object NoopTagRepository : TagRepository {
        override suspend fun getAll() = emptyList<Tag>()
        override suspend fun create(name: String, color: String) = 0L
        override suspend fun update(id: Long, name: String, color: String) {}
        override suspend fun delete(id: Long) {}
        override suspend fun getTagsForOpeningTxIds(openingTxIds: List<Long>) = emptyMap<Long, List<Tag>>()
        override suspend fun addTag(openingTxId: Long, tagId: Long) {}
        override suspend fun removeTag(openingTxId: Long, tagId: Long) {}
    }

    private fun newVm(service: ClosedPositionService) =
        CalendarViewModel(
            PositionTagService(service, NoopTagRepository),
            initialYear = 2026, initialMonth = 6, dispatcher = UnconfinedTestDispatcher(),
        )

    @Test
    fun `first load snaps to the latest trade month and sets bounds`() = runTest {
        val vm = newVm(serviceWith(mapOf(1L to listOf(pos(2024, 1, 10), pos(2024, 3, 20)))))
        vm.load(1L, Segment.ALL)

        val s = vm.state.value
        assertEquals(2024, s.year)
        assertEquals(3, s.month)
        assertTrue(s.canGoPrevious)
        assertFalse(s.canGoNext)
    }

    @Test
    fun `reload with the same portfolio and segment keeps the browsed month`() = runTest {
        val vm = newVm(serviceWith(mapOf(1L to listOf(pos(2024, 1, 10), pos(2024, 3, 20)))))
        vm.load(1L, Segment.ALL)
        vm.previousMonth()
        assertEquals(2 to 2024, vm.state.value.month to vm.state.value.year)

        vm.load(1L, Segment.ALL) // simulates returning to the calendar (e.g. back from Analysis)

        assertEquals(2024, vm.state.value.year)
        assertEquals(2, vm.state.value.month)
    }

    @Test
    fun `switching portfolio snaps to the latest month of the new dataset`() = runTest {
        val vm = newVm(
            serviceWith(
                mapOf(
                    1L to listOf(pos(2024, 1, 10), pos(2024, 3, 20)),
                    2L to listOf(pos(2023, 11, 5), pos(2023, 12, 8)),
                ),
            ),
        )
        vm.load(1L, Segment.ALL)
        vm.previousMonth()

        vm.load(2L, Segment.ALL)

        assertEquals(2023, vm.state.value.year)
        assertEquals(12, vm.state.value.month)
    }

    @Test
    fun `nextMonth stops at the latest trade month`() = runTest {
        val vm = newVm(serviceWith(mapOf(1L to listOf(pos(2024, 1, 10), pos(2024, 3, 20)))))
        vm.load(1L, Segment.ALL) // lands on 2024-03 (latest)

        vm.nextMonth()

        assertEquals(2024, vm.state.value.year)
        assertEquals(3, vm.state.value.month)
        assertFalse(vm.state.value.canGoNext)
    }

    @Test
    fun `previousMonth stops at the earliest trade month`() = runTest {
        val vm = newVm(serviceWith(mapOf(1L to listOf(pos(2024, 1, 10), pos(2024, 3, 20)))))
        vm.load(1L, Segment.ALL)
        vm.previousMonth() // 2024-02
        vm.previousMonth() // 2024-01 (earliest)
        vm.previousMonth() // no-op

        assertEquals(2024, vm.state.value.year)
        assertEquals(1, vm.state.value.month)
        assertFalse(vm.state.value.canGoPrevious)
    }

    @Test
    fun `reload with the same dataset preserves the selected day`() = runTest {
        val vm = newVm(serviceWith(mapOf(1L to listOf(pos(2024, 3, 20)))))
        vm.load(1L, Segment.ALL)
        vm.selectDay(LocalDate(2024, 3, 20))

        vm.load(1L, Segment.ALL)

        assertEquals(LocalDate(2024, 3, 20), vm.state.value.selectedDate)
    }

    @Test
    fun `switching dataset clears the selected day`() = runTest {
        val vm = newVm(
            serviceWith(
                mapOf(
                    1L to listOf(pos(2024, 3, 20)),
                    2L to listOf(pos(2023, 12, 8)),
                ),
            ),
        )
        vm.load(1L, Segment.ALL)
        vm.selectDay(LocalDate(2024, 3, 20))

        vm.load(2L, Segment.ALL)

        assertNull(vm.state.value.selectedDate)
    }
}
