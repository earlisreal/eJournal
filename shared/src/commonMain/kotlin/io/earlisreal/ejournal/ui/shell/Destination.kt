package io.earlisreal.ejournal.ui.shell

enum class Destination(val label: String, val enabled: Boolean) {
    DASHBOARD("Dashboard", enabled = false),
    TRADE_LOGS("Trade Logs", enabled = true),
    IMPORT("Import", enabled = true),
    CALENDAR("Calendar", enabled = false),
    ANALYSIS("Analysis", enabled = false);

    companion object {
        val DEFAULT = IMPORT
    }
}
