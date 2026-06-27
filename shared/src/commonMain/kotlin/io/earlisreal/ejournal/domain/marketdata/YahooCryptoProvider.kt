package io.earlisreal.ejournal.domain.marketdata

import kotlinx.datetime.LocalDate

/**
 * Serves crypto **daily** bars via Yahoo. Yahoo has deep history (years back) for the common coins,
 * whereas Alpaca's crypto bars only reach ~2021 and omit many altcoins entirely — so older crypto
 * trades have no daily data on Alpaca. Maps the stored bare symbol ("BTC", "Dash") to Yahoo's
 * `BASE-USD` pair ("BTC-USD", "DASH-USD"), delegating the fetch and Yahoo's quirk-handling to
 * [YahooFinanceProvider], then re-stamps the returned bars under the stored symbol so they match
 * `position.symbol` in storage. Crypto 1-min still routes to Alpaca (Yahoo has no deep intraday).
 */
class YahooCryptoProvider(private val delegate: MarketDataProvider) : MarketDataProvider {
    override suspend fun getBars(symbol: String, timeframe: Timeframe, from: LocalDate, to: LocalDate): List<Bar> {
        val pair = if (symbol.endsWith("-USD", ignoreCase = true)) symbol else "${symbol.uppercase()}-USD"
        return delegate.getBars(pair, timeframe, from, to).map { it.copy(symbol = symbol) }
    }
}
