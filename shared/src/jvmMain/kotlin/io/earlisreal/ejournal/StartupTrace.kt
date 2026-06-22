package io.earlisreal.ejournal

import java.util.Locale

/**
 * Records `System.nanoTime()` marks across the startup path and logs a one-line breakdown via the
 * file-logging tee. Mutable singleton on purpose: startup is a single shot, marks come from a few
 * threads (main + the init coroutine), so writes are synchronised.
 */
object StartupTrace {
    private val marks = mutableListOf<Pair<String, Long>>()

    @Synchronized
    fun mark(label: String) {
        marks.add(label to System.nanoTime())
    }

    @Synchronized
    fun summary(): String = renderTrace(marks)

    fun logSummary() {
        println("[startup] ${summary()}")
    }
}

/** Pure formatter: each mark as `label +<delta-from-prev>ms (<total-from-first>ms)`. */
internal fun renderTrace(marks: List<Pair<String, Long>>): String {
    if (marks.isEmpty()) return "no marks"
    val start = marks.first().second
    var prev = start
    return marks.joinToString("  |  ") { (label, ns) ->
        val sincePrev = (ns - prev) / 1_000_000.0
        val total = (ns - start) / 1_000_000.0
        prev = ns
        String.format(Locale.US, "%s +%.1fms (%.1fms)", label, sincePrev, total)
    }
}
