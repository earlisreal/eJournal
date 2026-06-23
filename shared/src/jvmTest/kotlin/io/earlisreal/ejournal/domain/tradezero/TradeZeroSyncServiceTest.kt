package io.earlisreal.ejournal.domain.tradezero

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.background.TaskState
import io.earlisreal.ejournal.testutil.FakePortfolioSettingsRepository
import io.earlisreal.ejournal.testutil.FakeTradeZeroClient
import io.earlisreal.ejournal.testutil.FakeTransactionRepository
import io.earlisreal.ejournal.testutil.tx
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TradeZeroSyncServiceTest {

    private val from = LocalDate(2026, 6, 1)
    private val to = LocalDate(2026, 6, 7)
    private val today = LocalDate(2026, 6, 23)

    private suspend fun FakePortfolioSettingsRepository.cursor(portfolioId: Long) =
        getString(portfolioId, TradeZeroSettings.LAST_SYNCED_DATE)

    @Test
    fun successImportsInsertsAllAndReportsSuccess() = runTest {
        val tracker = BackgroundTaskTracker()
        val client = FakeTradeZeroClient(TradeZeroFetchResult.Success(listOf(tx("tz:1"), tx("tz:2"), tx("tz:3"))))
        val repo = FakeTransactionRepository()
        val service = TradeZeroSyncService(client, repo, tracker, FakePortfolioSettingsRepository())

        val outcome = service.sync(portfolioId = 1, from = from, to = to)

        assertEquals(TradeZeroSyncOutcome.Imported(3), outcome)
        assertEquals(3, repo.inserted.size)
        assertEquals(1L, client.lastPortfolioId)
        val task = tracker.tasks.value.single()
        assertEquals(TaskState.Success, task.state)
        assertEquals("Imported 3 new transaction(s)", task.detail)
    }

    @Test
    fun successCountsOnlyNewlyInsertedTransactions() = runTest {
        val tracker = BackgroundTaskTracker()
        val client = FakeTradeZeroClient(TradeZeroFetchResult.Success(listOf(tx("tz:1"), tx("tz:2"), tx("tz:3"))))
        val repo = FakeTransactionRepository(duplicateExternalIds = setOf("tz:2", "tz:3"))
        val service = TradeZeroSyncService(client, repo, tracker, FakePortfolioSettingsRepository())

        val outcome = service.sync(portfolioId = 1, from = from, to = to)

        assertEquals(TradeZeroSyncOutcome.Imported(1), outcome)
        assertEquals(1, repo.inserted.size)
    }

    @Test
    fun invalidCredentialsReportsFailedTask() = runTest {
        val tracker = BackgroundTaskTracker()
        val client = FakeTradeZeroClient(TradeZeroFetchResult.InvalidCredentials)
        val service = TradeZeroSyncService(client, FakeTransactionRepository(), tracker, FakePortfolioSettingsRepository())

        val outcome = service.sync(portfolioId = 1, from = from, to = to)

        assertEquals(TradeZeroSyncOutcome.InvalidCredentials, outcome)
        assertEquals(TaskState.Failed, tracker.tasks.value.single().state)
    }

    @Test
    fun networkErrorReportsFailedTaskWithMessage() = runTest {
        val tracker = BackgroundTaskTracker()
        val client = FakeTradeZeroClient(TradeZeroFetchResult.NetworkError("timeout"))
        val service = TradeZeroSyncService(client, FakeTransactionRepository(), tracker, FakePortfolioSettingsRepository())

        val outcome = service.sync(portfolioId = 1, from = from, to = to)

        assertIs<TradeZeroSyncOutcome.NetworkError>(outcome)
        assertEquals("timeout", outcome.message)
        val task = tracker.tasks.value.single()
        assertEquals(TaskState.Failed, task.state)
        assertEquals("TradeZero network error: timeout", task.detail)
    }

    @Test
    fun firstIncrementalSyncBackfillsOneYearAndRecordsCursorOnSuccess() = runTest {
        val client = FakeTradeZeroClient(TradeZeroFetchResult.Success(listOf(tx("tz:1"))))
        val portfolioSettings = FakePortfolioSettingsRepository()
        val service = TradeZeroSyncService(client, FakeTransactionRepository(), BackgroundTaskTracker(), portfolioSettings, today = { today })

        val outcome = service.syncIncremental(portfolioId = 1)

        assertEquals(TradeZeroSyncOutcome.Imported(1), outcome)
        assertEquals(today.minus(365, DateTimeUnit.DAY), client.lastFrom)
        assertEquals(today, client.lastTo)
        assertEquals(today.toString(), portfolioSettings.cursor(1))
    }

    @Test
    fun incrementalSyncStartsFromCursorMinusOverlap() = runTest {
        val client = FakeTradeZeroClient(TradeZeroFetchResult.Success(emptyList()))
        val portfolioSettings = FakePortfolioSettingsRepository()
        portfolioSettings.putString(1, TradeZeroSettings.LAST_SYNCED_DATE, LocalDate(2026, 6, 1).toString())
        val service = TradeZeroSyncService(client, FakeTransactionRepository(), BackgroundTaskTracker(), portfolioSettings, today = { today })

        service.syncIncremental(portfolioId = 1)

        assertEquals(LocalDate(2026, 6, 1).minus(3, DateTimeUnit.DAY), client.lastFrom)
        assertEquals(today, client.lastTo)
        // A successful fetch with nothing new still advances the cursor.
        assertEquals(today.toString(), portfolioSettings.cursor(1))
    }

    @Test
    fun failedIncrementalSyncDoesNotAdvanceCursor() = runTest {
        val client = FakeTradeZeroClient(TradeZeroFetchResult.NetworkError("down"))
        val portfolioSettings = FakePortfolioSettingsRepository()
        portfolioSettings.putString(1, TradeZeroSettings.LAST_SYNCED_DATE, LocalDate(2026, 6, 1).toString())
        val service = TradeZeroSyncService(client, FakeTransactionRepository(), BackgroundTaskTracker(), portfolioSettings, today = { today })

        service.syncIncremental(portfolioId = 1)

        assertEquals(LocalDate(2026, 6, 1).toString(), portfolioSettings.cursor(1))
    }
}
