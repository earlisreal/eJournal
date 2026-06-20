package io.earlisreal.ejournal.domain.tradezero

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.background.TaskState
import io.earlisreal.ejournal.testutil.FakeTradeZeroClient
import io.earlisreal.ejournal.testutil.FakeTransactionRepository
import io.earlisreal.ejournal.testutil.tx
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TradeZeroSyncServiceTest {

    private val from = LocalDate(2026, 6, 1)
    private val to = LocalDate(2026, 6, 7)

    @Test
    fun successImportsInsertsAllAndReportsSuccess() = runTest {
        val tracker = BackgroundTaskTracker()
        val client = FakeTradeZeroClient(TradeZeroFetchResult.Success(listOf(tx("tz:1"), tx("tz:2"), tx("tz:3"))))
        val repo = FakeTransactionRepository()
        val service = TradeZeroSyncService(client, repo, tracker)

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
        val service = TradeZeroSyncService(client, repo, tracker)

        val outcome = service.sync(portfolioId = 1, from = from, to = to)

        assertEquals(TradeZeroSyncOutcome.Imported(1), outcome)
        assertEquals(1, repo.inserted.size)
    }

    @Test
    fun invalidCredentialsReportsFailedTask() = runTest {
        val tracker = BackgroundTaskTracker()
        val client = FakeTradeZeroClient(TradeZeroFetchResult.InvalidCredentials)
        val service = TradeZeroSyncService(client, FakeTransactionRepository(), tracker)

        val outcome = service.sync(portfolioId = 1, from = from, to = to)

        assertEquals(TradeZeroSyncOutcome.InvalidCredentials, outcome)
        assertEquals(TaskState.Failed, tracker.tasks.value.single().state)
    }

    @Test
    fun networkErrorReportsFailedTaskWithMessage() = runTest {
        val tracker = BackgroundTaskTracker()
        val client = FakeTradeZeroClient(TradeZeroFetchResult.NetworkError("timeout"))
        val service = TradeZeroSyncService(client, FakeTransactionRepository(), tracker)

        val outcome = service.sync(portfolioId = 1, from = from, to = to)

        assertIs<TradeZeroSyncOutcome.NetworkError>(outcome)
        assertEquals("timeout", outcome.message)
        val task = tracker.tasks.value.single()
        assertEquals(TaskState.Failed, task.state)
        assertEquals("TradeZero network error: timeout", task.detail)
    }
}
