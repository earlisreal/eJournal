package io.earlisreal.ejournal.background

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusSummaryTest {

    private fun task(
        id: String,
        state: TaskState,
        label: String = id,
        detail: String? = null,
        retry: (() -> Unit)? = null,
    ) = BackgroundTask(id, label, state, detail, retry)

    @Test
    fun emptyIsIdleReady() {
        val s = summarize(emptyList())
        assertEquals(StatusKind.Idle, s.kind)
        assertEquals("Ready", s.text)
    }

    @Test
    fun singleRunningShowsLabelAndDetail() {
        val s = summarize(listOf(task("md", TaskState.Running(TaskProgress.Determinate(3, 10)), "Market data", "3/10 symbols")))
        assertEquals(StatusKind.Running, s.kind)
        assertEquals("Market data — 3/10 symbols", s.text)
    }

    @Test
    fun singleRunningWithoutDetailShowsLabel() {
        val s = summarize(listOf(task("tz", TaskState.Running(), "TradeZero import")))
        assertEquals("TradeZero import", s.text)
    }

    @Test
    fun multipleRunningShowsCount() {
        val s = summarize(listOf(task("a", TaskState.Running(), "A"), task("b", TaskState.Running(), "B")))
        assertEquals(StatusKind.Running, s.kind)
        assertEquals("2 background tasks", s.text)
    }

    @Test
    fun runningTakesPriorityOverFailed() {
        val s = summarize(listOf(task("a", TaskState.Failed, "A", "boom"), task("b", TaskState.Running(), "B")))
        assertEquals(StatusKind.Running, s.kind)
    }

    @Test
    fun singleFailedShowsDetailAndRetry() {
        var retried = false
        val s = summarize(listOf(task("md", TaskState.Failed, "Market data", "Market data failed for 2 symbols", retry = { retried = true })))
        assertEquals(StatusKind.Failed, s.kind)
        assertEquals("Market data failed for 2 symbols", s.text)
        s.retry?.invoke()
        assertTrue(retried)
    }

    @Test
    fun multipleFailedShowsCountWithoutRetry() {
        val s = summarize(listOf(task("a", TaskState.Failed, "A", "x", retry = {}), task("b", TaskState.Failed, "B", "y", retry = {})))
        assertEquals(StatusKind.Failed, s.kind)
        assertEquals("2 tasks failed", s.text)
        assertNull(s.retry)
    }

    @Test
    fun failedTakesPriorityOverSuccess() {
        val s = summarize(listOf(task("a", TaskState.Success, "A", "done"), task("b", TaskState.Failed, "B", "boom")))
        assertEquals(StatusKind.Failed, s.kind)
    }

    @Test
    fun singleSuccessShowsDetail() {
        val s = summarize(listOf(task("md", TaskState.Success, "Market data", "Market data up to date")))
        assertEquals(StatusKind.Success, s.kind)
        assertEquals("Market data up to date", s.text)
    }

    @Test
    fun allSuccessMultipleShowsAggregate() {
        val s = summarize(listOf(
            task("md", TaskState.Success, "Market data", "up to date"),
            task("tz", TaskState.Success, "TradeZero", "imported 12"),
        ))
        assertEquals(StatusKind.Success, s.kind)
        assertEquals("All tasks complete", s.text)
    }
}
