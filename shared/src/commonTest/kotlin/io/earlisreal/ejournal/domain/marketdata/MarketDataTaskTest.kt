package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.background.TaskProgress
import io.earlisreal.ejournal.background.TaskState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketDataTaskTest {

    private val noRetry: () -> Unit = {}

    @Test
    fun idleMapsToNull() {
        assertNull(SyncStatus.Idle.toBackgroundTask(noRetry))
    }

    @Test
    fun syncingMapsToDeterminateRunning() {
        val task = SyncStatus.Syncing(3, 10).toBackgroundTask(noRetry)!!
        assertEquals("market-data", task.id)
        assertEquals("Market data", task.label)
        val state = task.state
        assertIs<TaskState.Running>(state)
        assertEquals(TaskProgress.Determinate(3, 10), state.progress)
        assertEquals("3/10 symbols", task.detail)
    }

    @Test
    fun syncingWithNoWorkIsIndeterminate() {
        val task = SyncStatus.Syncing(0, 0).toBackgroundTask(noRetry)!!
        val state = task.state
        assertIs<TaskState.Running>(state)
        assertEquals(TaskProgress.Indeterminate, state.progress)
    }

    @Test
    fun finishedUpToDateMapsToSuccess() {
        val task = SyncStatus.Finished(SyncResult(0, emptyList(), keysRejected = false, needsKeys = false))
            .toBackgroundTask(noRetry)!!
        assertEquals(TaskState.Success, task.state)
        assertEquals("Market data up to date", task.detail)
        assertNull(task.retry)
    }

    @Test
    fun finishedFetchedMapsToSuccessWithCount() {
        val task = SyncStatus.Finished(SyncResult(5, emptyList(), keysRejected = false, needsKeys = false))
            .toBackgroundTask(noRetry)!!
        assertEquals(TaskState.Success, task.state)
        assertEquals("Market data fetched for 5 symbol(s)", task.detail)
    }

    @Test
    fun finishedWithFailedSymbolsMapsToFailedWithRetry() {
        var retried = false
        val task = SyncStatus.Finished(SyncResult(2, listOf("AAPL", "TSLA"), keysRejected = false, needsKeys = false))
            .toBackgroundTask { retried = true }!!
        assertEquals(TaskState.Failed, task.state)
        assertEquals("Market data failed for 2 symbol(s)", task.detail)
        task.retry?.invoke()
        assertTrue(retried)
    }

    @Test
    fun finishedWithRejectedKeysMapsToFailed() {
        val task = SyncStatus.Finished(SyncResult(0, emptyList(), keysRejected = true, needsKeys = false))
            .toBackgroundTask(noRetry)!!
        assertEquals(TaskState.Failed, task.state)
        assertEquals("Alpaca keys rejected — check Settings", task.detail)
    }

    @Test
    fun finishedNeedsKeysMapsToSuccessWithHint() {
        val task = SyncStatus.Finished(SyncResult(1, emptyList(), keysRejected = false, needsKeys = true))
            .toBackgroundTask(noRetry)!!
        assertEquals(TaskState.Success, task.state)
        assertEquals("Market data synced — add Alpaca keys in Settings for intraday older than 30 days", task.detail)
    }
}
