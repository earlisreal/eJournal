package io.earlisreal.ejournal.background

enum class StatusKind { Running, Success, Failed, Idle }

/** Collapsed one-line view of all background tasks shown in the status bar. */
data class StatusSummary(
    val kind: StatusKind,
    val text: String,
    val retry: (() -> Unit)? = null,
)

/**
 * Reduce the full task list to a single line. Running takes priority over Failed over
 * Success; a lone task shows its own detail, several collapse to a count.
 */
fun summarize(tasks: List<BackgroundTask>): StatusSummary {
    val running = tasks.filter { it.state is TaskState.Running }
    if (running.isNotEmpty()) {
        return if (running.size == 1) {
            val t = running.first()
            StatusSummary(StatusKind.Running, t.detail?.let { "${t.label} — $it" } ?: t.label)
        } else {
            StatusSummary(StatusKind.Running, "${running.size} background tasks")
        }
    }

    val failed = tasks.filter { it.state is TaskState.Failed }
    if (failed.isNotEmpty()) {
        return if (failed.size == 1) {
            val t = failed.first()
            StatusSummary(StatusKind.Failed, t.detail ?: "${t.label} failed", retry = t.retry)
        } else {
            StatusSummary(StatusKind.Failed, "${failed.size} tasks failed")
        }
    }

    val success = tasks.filter { it.state is TaskState.Success }
    if (success.isNotEmpty()) {
        return if (success.size == 1) {
            val t = success.first()
            StatusSummary(StatusKind.Success, t.detail ?: "${t.label} complete")
        } else {
            StatusSummary(StatusKind.Success, "All tasks complete")
        }
    }

    return StatusSummary(StatusKind.Idle, "Ready")
}
