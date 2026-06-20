package io.earlisreal.ejournal.background

/** Progress of a running task: either a spinner (unknown duration) or a count. */
sealed interface TaskProgress {
    data object Indeterminate : TaskProgress
    data class Determinate(val completed: Int, val total: Int) : TaskProgress
}

sealed interface TaskState {
    data class Running(val progress: TaskProgress = TaskProgress.Indeterminate) : TaskState
    data object Success : TaskState
    data object Failed : TaskState
}

/**
 * A single background activity surfaced in the status bar. Identified by a stable [id]
 * (e.g. "market-data", "tradezero-import") so re-runs replace the previous entry rather
 * than piling up, and finished tasks linger to show the last result.
 */
data class BackgroundTask(
    val id: String,
    val label: String,
    val state: TaskState,
    val detail: String? = null,
    val retry: (() -> Unit)? = null,
)

/** Handle returned by [BackgroundTaskTracker.start] for one-shot work to report its outcome. */
interface TaskHandle {
    fun succeed(detail: String? = null)
    fun fail(detail: String? = null, retry: (() -> Unit)? = null)
}
