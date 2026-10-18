package io.earlisreal.ejournal.ui.viewmodel

import io.earlisreal.ejournal.data.repository.InsertOrEnrichResult

import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.ClosedPositionService
import io.earlisreal.ejournal.domain.FifoMatcher
import io.earlisreal.ejournal.domain.PositionTagService
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.calendarAnalysisPositions
import io.earlisreal.ejournal.domain.analytics.TagMatch
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Action
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

    private fun posAt(dt: LocalDateTime, pnl: Double = 0.0): ClosedPosition {
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

    private fun pos(year: Int, month: Int, day: Int, pnl: Double = 0.0): ClosedPosition =
        posAt(LocalDateTime(year, month, day, 15, 0), pnl)

    private fun swingPosition(firstExit: String = "2024-05-31T23:59", lastExit: String = "2024-06-01T00:01") =
        FifoMatcher.computeClosedPositions(listOf(
            tx("open").copy(id = 1, datetime = LocalDateTime.parse("2024-05-27T09:00"), price = 10.0, shares = 100.0, fees = 10.0),
            tx("partial").copy(id = 2, datetime = LocalDateTime.parse(firstExit), action = Action.SELL, price = 12.0, shares = 40.0, fees = 4.0),
            tx("close").copy(id = 3, datetime = LocalDateTime.parse(lastExit), action = Action.SELL, price = 15.0, shares = 60.0, fees = 6.0),
        )).single()

    /** ClosedPositionService backed by a per-portfolio marker tx + a compute that maps it to fixtures. */
    private fun serviceWith(positions: Map<Long, List<ClosedPosition>>): ClosedPositionService {
        val repo = object : TransactionRepository {
            override suspend fun getByPortfolio(portfolioId: Long): List<Transaction> =
                listOf(tx(externalId = "p$portfolioId:${positions[portfolioId].hashCode()}"))
            override suspend fun getByPortfolioAndDateRange(portfolioId: Long, from: LocalDateTime, to: LocalDateTime): List<Transaction> = emptyList()
            override suspend fun insertOrEnrich(transaction: Transaction): InsertOrEnrichResult =
                if (insert(transaction) != null) InsertOrEnrichResult.INSERTED else InsertOrEnrichResult.UNCHANGED

            override suspend fun insert(transaction: Transaction): Long? = null
            override suspend fun delete(id: Long) {}
            override suspend fun countByPortfolio(portfolioId: Long): Long = 0
            override suspend fun deleteByPortfolio(portfolioId: Long) {}
        }
        val compute: (List<Transaction>) -> List<ClosedPosition> = { txs ->
            val pid = txs.first().externalId!!.removePrefix("p").substringBefore(':').toLong()
            positions[pid] ?: emptyList()
        }
        return ClosedPositionService(repo, FakePortfolioRepository(), compute)
    }

    private object NoopTagRepository : TagRepository {
        override suspend fun getAll(portfolioId: Long) = emptyList<Tag>()
        override suspend fun create(portfolioId: Long, name: String, color: String) = 0L
        override suspend fun update(portfolioId: Long, id: Long, name: String, color: String) {}
        override suspend fun delete(portfolioId: Long, id: Long) {}
        override suspend fun getTagsForOpeningTxIds(portfolioId: Long, openingTxIds: List<Long>) = emptyMap<Long, List<Tag>>()
        override suspend fun addTag(portfolioId: Long, openingTxId: Long, tagId: Long) {}
        override suspend fun removeTag(portfolioId: Long, openingTxId: Long, tagId: Long) {}
    }

    private fun newVm(service: ClosedPositionService) =
        CalendarViewModel(
            PositionTagService(service, NoopTagRepository),
            initialYear = 2026, initialMonth = 6, dispatcher = UnconfinedTestDispatcher(),
        )

    @Test
    fun `partial exit months and complete weeks reconcile with original Analysis position`() = runTest {
        val position = swingPosition()
        val vm = newVm(serviceWith(mapOf(1L to listOf(position))))
        vm.load(1L, Segment.ALL)

        assertEquals(6, vm.state.value.month)
        assertEquals(288.0, vm.state.value.monthTotal)
        assertTrue(vm.state.value.canGoPrevious)
        val entries = vm.entriesForWeek(LocalDate(2024, 5, 27))
        assertEquals(listOf(LocalDate(2024, 5, 31), LocalDate(2024, 6, 1)), entries.map { it.date })
        assertEquals(listOf(40.0, 60.0), entries.map { it.shares })
        assertEquals(360.0, entries.sumOf { it.profitLoss })
        assertEquals(listOf(position), calendarAnalysisPositions(entries))
        assertEquals(position, entries.first().position)

        vm.selectWeek(LocalDate(2024, 5, 27))
        vm.load(1L, Segment.ALL)
        assertEquals(LocalDate(2024, 5, 27), vm.state.value.selectedWeekStart)
        vm.selectDay(LocalDate(2024, 5, 31))
        assertEquals(5, vm.state.value.month)
        assertEquals(72.0, vm.state.value.monthTotal)
        assertFalse(vm.state.value.canGoPrevious)
        assertTrue(vm.state.value.canGoNext)
        vm.load(1L, Segment.ALL)
        assertEquals(LocalDate(2024, 5, 31), vm.state.value.selectedDate)
    }

    @Test
    fun `new earlier exit clears selection even when final date and total Pnl are unchanged`() = runTest {
        val transactions = listOf(
            tx("open").copy(id = 1, datetime = LocalDateTime.parse("2024-05-27T09:00"), price = 10.0, shares = 100.0, fees = 10.0),
            tx("close").copy(id = 3, datetime = LocalDateTime.parse("2024-06-06T09:00"), action = Action.SELL, price = 12.0, shares = 100.0, fees = 10.0),
        )
        val positions = mutableMapOf(1L to FifoMatcher.computeClosedPositions(transactions))
        val vm = newVm(serviceWith(positions))
        vm.load(1L, Segment.ALL)
        vm.selectWeek(LocalDate(2024, 6, 3))

        positions[1L] = FifoMatcher.computeClosedPositions(listOf(
            transactions[0],
            transactions[1].copy(id = 2, datetime = LocalDateTime.parse("2024-05-31T09:00"), shares = 40.0, fees = 4.0),
            transactions[1].copy(shares = 60.0, fees = 6.0),
        ))
        assertEquals(180.0, positions[1L]!!.single().profitLoss)
        vm.load(1L, Segment.ALL)

        assertNull(vm.state.value.selectedWeekStart)
        assertNull(vm.state.value.selectedDate)
        assertEquals(6, vm.state.value.month)
        assertEquals(108.0, vm.state.value.monthTotal)
        assertEquals(72.0, vm.state.value.summaries[LocalDate(2024, 5, 31)]?.netPnl)
        assertTrue(vm.state.value.canGoPrevious)
    }

    @Test
    fun `same day partial exit inherits parent Swing classification and tags`() = runTest {
        val tag = Tag(7, 1, "Setup", "#111111")
        val position = swingPosition("2024-05-27T10:00", "2024-05-28T10:00").copy(tags = listOf(tag))
        val vm = newVm(serviceWith(mapOf(1L to listOf(position))))
        vm.load(1L, Segment.SWING, setOf(tag.id), TagMatch.ANY)

        assertEquals(72.0, vm.state.value.summaries[LocalDate(2024, 5, 27)]?.netPnl)
        assertEquals(288.0, vm.state.value.summaries[LocalDate(2024, 5, 28)]?.netPnl)
        assertEquals(listOf(position), calendarAnalysisPositions(vm.entriesForWeek(LocalDate(2024, 5, 27))))

        vm.load(1L, Segment.DAY, setOf(tag.id), TagMatch.ANY)
        assertTrue(vm.state.value.summaries.isEmpty())
        vm.load(1L, Segment.SWING, setOf(8L), TagMatch.ANY)
        assertTrue(vm.state.value.summaries.isEmpty())
    }

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
    fun `day and week selections are mutually exclusive`() = runTest {
        val vm = newVm(serviceWith(mapOf(1L to listOf(pos(2024, 3, 20)))))
        vm.load(1L, Segment.ALL)

        vm.selectDay(LocalDate(2024, 3, 20))
        assertEquals(LocalDate(2024, 3, 20), vm.state.value.selectedDate)
        assertNull(vm.state.value.selectedWeekStart)

        vm.selectWeek(LocalDate(2024, 3, 18))
        assertNull(vm.state.value.selectedDate)
        assertEquals(LocalDate(2024, 3, 18), vm.state.value.selectedWeekStart)
    }

    @Test
    fun `month changes clear both selections`() = runTest {
        val vm = newVm(serviceWith(mapOf(1L to listOf(pos(2024, 3, 20), pos(2024, 4, 2)))))
        vm.load(1L, Segment.ALL)
        vm.selectWeek(LocalDate(2024, 3, 18))

        vm.jumpToMonth(2024, 4)

        assertNull(vm.state.value.selectedDate)
        assertNull(vm.state.value.selectedWeekStart)
    }

    @Test
    fun `reload with the same dataset preserves the selected week`() = runTest {
        val vm = newVm(serviceWith(mapOf(1L to listOf(pos(2024, 3, 20)))))
        vm.load(1L, Segment.ALL)
        vm.selectWeek(LocalDate(2024, 3, 18))

        vm.load(1L, Segment.ALL)

        assertEquals(LocalDate(2024, 3, 18), vm.state.value.selectedWeekStart)
        assertNull(vm.state.value.selectedDate)
    }

    @Test
    fun `dataset changes clear the selected week even when the filter key is unchanged`() = runTest {
        val positions = mutableMapOf(1L to listOf(pos(2024, 3, 20)))
        val vm = newVm(serviceWith(positions))
        vm.load(1L, Segment.ALL)
        vm.selectWeek(LocalDate(2024, 3, 18))

        positions[1L] = listOf(pos(2024, 4, 2))
        vm.load(1L, Segment.ALL)

        assertNull(vm.state.value.selectedDate)
        assertNull(vm.state.value.selectedWeekStart)
    }

    @Test
    fun `selecting a traded adjacent day changes the displayed month`() = runTest {
        val vm = newVm(serviceWith(mapOf(1L to listOf(pos(2024, 4, 1)))))
        vm.load(1L, Segment.ALL)
        vm.jumpToMonth(2024, 3)

        vm.selectDay(LocalDate(2024, 4, 1))

        assertEquals(2024, vm.state.value.year)
        assertEquals(4, vm.state.value.month)
        assertEquals(LocalDate(2024, 4, 1), vm.state.value.selectedDate)
        assertNull(vm.state.value.selectedWeekStart)
    }

    @Test
    fun `weekly positions include only seven dates and are ordered by exit datetime`() = runTest {
        val early = posAt(LocalDateTime(2024, 6, 3, 9, 0), pnl = 10.0)
        val late = posAt(LocalDateTime(2024, 6, 3, 15, 0), pnl = 20.0)
        val sunday = posAt(LocalDateTime(2024, 6, 9, 12, 0), pnl = 30.0)
        val before = posAt(LocalDateTime(2024, 6, 2, 15, 0), pnl = 40.0)
        val after = posAt(LocalDateTime(2024, 6, 10, 9, 0), pnl = 50.0)
        val vm = newVm(serviceWith(mapOf(1L to listOf(after, sunday, late, before, early))))
        vm.load(1L, Segment.ALL)

        assertEquals(listOf(early, late, sunday), calendarAnalysisPositions(vm.entriesForWeek(LocalDate(2024, 6, 3))))
    }

    @Test
    fun `calendar applies segment and tag filters to daily and weekly values`() = runTest {
        val tag = Tag(7, 1, "Setup", "#111111")
        val taggedDay = posAt(LocalDateTime(2024, 6, 3, 9, 0), pnl = 10.0).copy(tags = listOf(tag))
        val untaggedDay = posAt(LocalDateTime(2024, 6, 4, 9, 0), pnl = 20.0)
        val taggedSwing = posAt(LocalDateTime(2024, 6, 5, 9, 0), pnl = 30.0)
            .copy(entryDatetime = LocalDateTime(2024, 6, 4, 9, 0), tags = listOf(tag))
        val vm = newVm(serviceWith(mapOf(1L to listOf(taggedSwing, untaggedDay, taggedDay))))

        vm.load(1L, Segment.DAY, setOf(tag.id), TagMatch.ANY)

        assertEquals(10.0, vm.state.value.summaries[LocalDate(2024, 6, 3)]?.netPnl)
        assertEquals(listOf(taggedDay), calendarAnalysisPositions(vm.entriesForWeek(LocalDate(2024, 6, 3))))
    }

    @Test
    fun `month total excludes adjacent dates while the week includes them`() = runTest {
        val mayPosition = pos(2024, 5, 31, pnl = 25.0)
        val junePosition = pos(2024, 6, 1, pnl = 75.0)
        val vm = newVm(serviceWith(mapOf(1L to listOf(mayPosition, junePosition))))
        vm.load(1L, Segment.ALL)

        assertEquals(75.0, vm.state.value.monthTotal)
        assertEquals(listOf(mayPosition, junePosition), calendarAnalysisPositions(vm.entriesForWeek(LocalDate(2024, 5, 27))))

        vm.jumpToMonth(2024, 5)

        assertEquals(25.0, vm.state.value.monthTotal)
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
