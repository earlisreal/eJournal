package io.earlisreal.ejournal.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DestinationTest {

    @Test
    fun destinationsAreInSidebarOrder() {
        assertEquals(
            listOf("Dashboard", "Trade Logs", "Import", "Calendar", "Analysis"),
            Destination.entries.map { it.label }
        )
    }

    @Test
    fun dashboardTradeLogsAndImportAreEnabled() {
        assertEquals(
            setOf(Destination.DASHBOARD, Destination.TRADE_LOGS, Destination.IMPORT),
            Destination.entries.filter { it.enabled }.toSet()
        )
    }

    @Test
    fun defaultDestinationIsImport() {
        assertEquals(Destination.IMPORT, Destination.DEFAULT)
        assertTrue(Destination.DEFAULT.enabled)
    }
}
