package io.earlisreal.ejournal.ui.chart

/**
 * Warm-once / take-once handoff for preloading an expensive UI object off the critical path.
 * [warm] builds the instance exactly once via [onUiThread] (the chart bridge's JFXPanel must be
 * created on the AWT EDT); [take] hands the built instance to the first consumer, then returns null.
 * Generic + injected factory/dispatcher so this logic is testable without JavaFX.
 */
class ChartPreloader<T : Any>(
    private val create: () -> T,
    private val onUiThread: (() -> Unit) -> Unit,
) {
    private val lock = Any()
    private var started = false
    private var instance: T? = null

    fun warm() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        onUiThread {
            val built = create()
            synchronized(lock) { instance = built }
        }
    }

    fun take(): T? = synchronized(lock) {
        val taken = instance
        instance = null
        taken
    }
}
