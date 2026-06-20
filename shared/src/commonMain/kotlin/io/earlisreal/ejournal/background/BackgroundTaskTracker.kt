package io.earlisreal.ejournal.background

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * App-wide registry of background activities for the status bar. Generic on purpose: any
 * background operation reports here and the bar renders whatever is present. Mutations use
 * [MutableStateFlow.update] (atomic, lock-free) so callers on any thread are safe.
 */
class BackgroundTaskTracker {

    private val _tasks = MutableStateFlow<List<BackgroundTask>>(emptyList())
    val tasks: StateFlow<List<BackgroundTask>> = _tasks.asStateFlow()

    /** Upsert by [BackgroundTask.id], preserving position for an existing id. */
    fun update(task: BackgroundTask) {
        _tasks.update { list ->
            val index = list.indexOfFirst { it.id == task.id }
            if (index >= 0) list.toMutableList().also { it[index] = task }
            else list + task
        }
    }

    /** Register a running task and return a handle to report its outcome. */
    fun start(id: String, label: String, detail: String? = null): TaskHandle {
        update(BackgroundTask(id, label, TaskState.Running(), detail))
        return object : TaskHandle {
            override fun succeed(detail: String?) {
                update(BackgroundTask(id, label, TaskState.Success, detail))
            }

            override fun fail(detail: String?, retry: (() -> Unit)?) {
                update(BackgroundTask(id, label, TaskState.Failed, detail, retry))
            }
        }
    }
}
