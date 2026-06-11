package io.earlisreal.ejournal.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MarketTest {

    @Test
    fun labelsAndSymbolsAreAsSpecified() {
        assertEquals("US Stocks", Market.US_STOCKS.label)
        assertEquals("$", Market.US_STOCKS.symbol)
        assertEquals("PH Stocks", Market.PH_STOCKS.label)
        assertEquals("₱", Market.PH_STOCKS.symbol)
        assertEquals("Crypto", Market.CRYPTO.label)
        assertEquals("$", Market.CRYPTO.symbol)
    }
}
