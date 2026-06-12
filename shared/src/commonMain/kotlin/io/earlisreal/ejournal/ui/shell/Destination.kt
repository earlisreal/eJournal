package io.earlisreal.ejournal.ui.shell

enum class Destination(val label: String, val enabled: Boolean, val pinnedBottom: Boolean = false) {
    DASHBOARD("Dashboard", enabled = true),
    TRADE_LOGS("Trade Logs", enabled = true),
    IMPORT("Import", enabled = true),
    CALENDAR("Calendar", enabled = true),
    ANALYSIS("Analysis", enabled = false),
    SETTINGS("Settings", enabled = true, pinnedBottom = true);

    companion object {
        val DEFAULT = IMPORT
    }
}
