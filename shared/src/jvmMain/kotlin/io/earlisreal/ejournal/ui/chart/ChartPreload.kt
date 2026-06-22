package io.earlisreal.ejournal.ui.chart

import io.earlisreal.ejournal.StartupTrace
import java.awt.EventQueue
import javax.swing.SwingUtilities

/** App-wide chart preloader: builds one JavaFxChartBridge on the EDT, off the startup critical path. */
object ChartPreload {
    private val preloader = ChartPreloader<JavaFxChartBridge>(
        create = {
            val bridge = JavaFxChartBridge()
            StartupTrace.mark("chart-warm-done")
            StartupTrace.logSummary()
            bridge
        },
        onUiThread = { block ->
            if (EventQueue.isDispatchThread()) block() else SwingUtilities.invokeLater(block)
        },
    )

    fun warm() = preloader.warm()
    fun take(): JavaFxChartBridge? = preloader.take()
}
