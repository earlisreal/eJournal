package io.earlisreal.ejournal.domain.model

/** A portfolio's market. `symbol` is the display currency symbol; Phase 7 routes scrapers by market. */
enum class Market(val label: String, val symbol: String) {
    US_STOCKS("US Stocks", "$"),
    PH_STOCKS("PH Stocks", "₱"),
    CRYPTO("Crypto", "$"),
}
