package io.earlisreal.ejournal.background

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackgroundTaskTrackerTest {

    @Test
    fun startsWithNoTasks() {
        val tracker = BackgroundTaskTracker()
        assertEquals(emptyList(), tracker.tasks.value)
    }

    @Test
    fun updateAddsTask() {
        val tracker = BackgroundTaskTracker()
        tracker.update(BackgroundTask("md", "Market data", TaskState.Running()))
        assertEquals(1, tracker.tasks.value.size)
        assertEquals("Market data", tracker.tasks.value.first().label)
    }

    @Test
    fun updateReplacesSameIdInPlace() {
        val tracker = BackgroundTaskTracker()
        tracker.update(BackgroundTask("md", "Market data", TaskState.Running(TaskProgress.Determinate(0, 10))))
        tracker.update(BackgroundTask("tz", "TradeZero", TaskState.Running()))
        tracker.update(BackgroundTask("md", "Market data", TaskState.Success, detail = "Up to date"))

        val tasks = tracker.tasks.value
        assertEquals(2, tasks.size, "same id must not duplicate")
        assertEquals("md", tasks[0].id, "position preserved on replace")
        assertEquals(TaskState.Success, tasks[0].state)
        assertEquals("Up to date", tasks[0].detail)
        assertEquals("tz", tasks[1].id)
    }

    @Test
    fun newIdsAppendInInsertionOrder() {
        val tracker = BackgroundTaskTracker()
        tracker.update(BackgroundTask("a", "A", TaskState.Running()))
        tracker.update(BackgroundTask("b", "B", TaskState.Running()))
        assertEquals(listOf("a", "b"), tracker.tasks.value.map { it.id })
    }

    @Test
    fun startRegistersRunningTask() {
        val tracker = BackgroundTaskTracker()
        tracker.start("tz", "TradeZero import", detail = "Fetching orders…")
        val task = tracker.tasks.value.single()
        assertEquals("tz", task.id)
        assertIs<TaskState.Running>(task.state)
        assertEquals("Fetching orders…", task.detail)
    }

    @Test
    fun handleSucceedMarksSuccess() {
        val tracker = BackgroundTaskTracker()
        val handle = tracker.start("tz", "TradeZero import")
        handle.succeed("Imported 12 orders")
        val task = tracker.tasks.value.single()
        assertEquals(TaskState.Success, task.state)
        assertEquals("Imported 12 orders", task.detail)
    }

    @Test
    fun handleFailMarksFailedWithRetryAndPreservesLabel() {
        val tracker = BackgroundTaskTracker()
        val handle = tracker.start("tz", "TradeZero import")
        var retried = false
        handle.fail("Network error", retry = { retried = true })

        val task = tracker.tasks.value.single()
        assertEquals(TaskState.Failed, task.state)
        assertEquals("Network error", task.detail)
        assertEquals("TradeZero import", task.label)
        task.retry?.invoke()
        assertTrue(retried)
    }
}
