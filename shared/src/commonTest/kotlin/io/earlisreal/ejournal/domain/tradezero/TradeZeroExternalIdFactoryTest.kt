package io.earlisreal.ejournal.domain.tradezero

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TradeZeroExternalIdFactoryTest {

    @Test
    fun buildsNaturalKeyFromSymbolSecondSideAndShares() {
        val id = TradeZeroExternalIdFactory()
            .create("AAPL", LocalDateTime.parse("2026-06-16T00:00:00"), Action.BUY, 100.0)
        assertEquals("tz:AAPL:2026-06-16T00:00:00:BUY:100.0#0", id)
    }

    @Test
    fun assignsIncrementingOrdinalsToIdenticalFills() {
        val factory = TradeZeroExternalIdFactory()
        val dt = LocalDateTime.parse("2021-06-24T10:29:39")
        assertEquals("tz:SE:2021-06-24T10:29:39:BUY:2.0#0", factory.create("SE", dt, Action.BUY, 2.0))
        assertEquals("tz:SE:2021-06-24T10:29:39:BUY:2.0#1", factory.create("SE", dt, Action.BUY, 2.0))
    }

    @Test
    fun truncatesSubSecondPrecisionToTheSecond() {
        val factory = TradeZeroExternalIdFactory()
        // A whole-second and a sub-second fill in the same second must share a base key (one group),
        // so the API's sub-second instants align with the CSV's second-granular times.
        assertEquals(
            "tz:SE:2021-06-24T10:29:39:BUY:2.0#0",
            factory.create("SE", LocalDateTime.parse("2021-06-24T10:29:39"), Action.BUY, 2.0),
        )
        assertEquals(
            "tz:SE:2021-06-24T10:29:39:BUY:2.0#1",
            factory.create("SE", LocalDateTime.parse("2021-06-24T10:29:39.500"), Action.BUY, 2.0),
        )
    }

    @Test
    fun distinctGroupsEachStartAtOrdinalZero() {
        val factory = TradeZeroExternalIdFactory()
        val dt = LocalDateTime.parse("2021-06-24T10:29:39")
        assertEquals("tz:SE:2021-06-24T10:29:39:BUY:2.0#0", factory.create("SE", dt, Action.BUY, 2.0))
        assertEquals("tz:SE:2021-06-24T10:29:39:SELL:2.0#0", factory.create("SE", dt, Action.SELL, 2.0))
        assertEquals("tz:SE:2021-06-24T10:29:39:BUY:3.0#0", factory.create("SE", dt, Action.BUY, 3.0))
    }
}
