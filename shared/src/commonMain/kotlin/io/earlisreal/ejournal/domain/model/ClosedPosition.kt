package io.earlisreal.ejournal.domain.model

import kotlinx.datetime.LocalDateTime

data class ClosedPosition(
    val symbol: String,
    val entryDatetime: LocalDateTime,
    val exitDatetime: LocalDateTime,
    val averageEntryPrice: Double,
    val averageExitPrice: Double,
    val shares: Double,
    val fees: Double,
    val profitLoss: Double,
    val transactions: List<Transaction> = emptyList(),
    val direction: TradeDirection = TradeDirection.LONG,
    // The portfolio's market — stamped by ClosedPositionService so the market-data layer can
    // namespace bars (a crypto "BTC" and a stock "BTC" are distinct instruments). Defaults to US
    // stocks for the FifoMatcher/test/preview constructors that don't carry a portfolio.
    val market: Market = Market.US_STOCKS,
    // User tags, hydrated by PositionTagService after FIFO recomputation (never produced by
    // FifoMatcher itself). Keyed to this position's opening transaction id.
    val tags: List<Tag> = emptyList(),
) {
    /**
     * Stable identity for tag assignment: the id of the fill that opened this round trip.
     * FifoMatcher appends fills in datetime order, so the first transaction is the opener. Null only
     * for positions built without their transactions (tests/previews).
     */
    val openingTransactionId: Long?
        get() = transactions.firstOrNull()?.id
}
