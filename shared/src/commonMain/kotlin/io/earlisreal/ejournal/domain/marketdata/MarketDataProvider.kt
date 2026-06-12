package io.earlisreal.ejournal.domain.marketdata

import kotlinx.datetime.LocalDate

/** Fetches OHLCV bars for an inclusive day range. Implementations are per data source. */
interface MarketDataProvider {
    suspend fun getBars(symbol: String, timeframe: Timeframe, from: LocalDate, to: LocalDate): List<Bar>
}

/** The provider rejected our credentials — retrying other symbols would also fail. */
class InvalidKeysException(message: String) : Exception(message)

/** The symbol has no data at this provider (unknown/delisted) — retrying won't help. */
class SymbolNotFoundException(symbol: String) : Exception("No data for symbol $symbol")

/** Network/server trouble — worth one retry, then skip and let the next pass heal it. */
class TransientFetchException(message: String, cause: Throwable? = null) : Exception(message, cause)
