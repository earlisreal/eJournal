package io.earlisreal.ejournal.domain.marketdata

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingProvider : MarketDataProvider {
    var lastSymbol: String? = null
    override suspend fun getBars(symbol: String, timeframe: Timeframe, from: LocalDate, to: LocalDate): List<Bar> {
        lastSymbol = symbol
        // Bars come back stamped with the wire symbol the delegate was asked for.
        return listOf(Bar(symbol, timeframe, LocalDateTime.parse("2021-02-12T00:00"), 1.0, 2.0, 0.5, 1.5, 100L))
    }
}

class YahooCryptoProviderTest {

    @Test
    fun `maps a bare symbol to the -USD pair and re-stamps bars under the stored symbol`() = runTest {
        val delegate = RecordingProvider()
        val bars = YahooCryptoProvider(delegate)
            .getBars("BTC", Timeframe.DAILY, LocalDate.parse("2021-02-01"), LocalDate.parse("2021-03-01"))

        assertEquals("BTC-USD", delegate.lastSymbol) // queried Yahoo with the pair
        assertEquals("BTC", bars.single().symbol)    // stored back under the bare symbol
    }

    @Test
    fun `uppercases mixed-case symbols (eToro stores Dash, Yahoo wants DASH-USD)`() = runTest {
        val delegate = RecordingProvider()
        val bars = YahooCryptoProvider(delegate)
            .getBars("Dash", Timeframe.DAILY, LocalDate.parse("2021-02-01"), LocalDate.parse("2021-03-01"))

        assertEquals("DASH-USD", delegate.lastSymbol)
        assertEquals("Dash", bars.single().symbol)
    }
}
