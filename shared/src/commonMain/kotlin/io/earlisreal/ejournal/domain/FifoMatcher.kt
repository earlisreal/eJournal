package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.TradeDirection
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime

/**
 * Groups a symbol's transactions into round-trip trades. A trade runs from the moment a position
 * opens (from flat) to the moment it returns to flat, aggregating every scale-in and scale-out
 * tranche into a single [ClosedPosition]. Supports long (buy-to-open) and short (sell-to-open)
 * trips, including position flips. The realized portion of a still-open position at the end of the
 * data is emitted as a trade so realized P&L stays visible.
 */
object FifoMatcher {

    /** An open lot on the position's opening side: a buy lot for a long trip, a sell lot for a short trip. */
    private data class Lot(
        val price: Double,
        var remainingShares: Double,
        val totalShares: Double,
        val totalFee: Double,
    )

    /** Accumulates one round-trip trade as transactions are matched against it. */
    private class TradeAccumulator(val direction: TradeDirection, val openDatetime: LocalDateTime) {
        var matchedShares = 0.0
        var openValue = 0.0      // Σ openPrice  × matchedShares  (for the average entry price)
        var closeValue = 0.0     // Σ closePrice × matchedShares  (for the average exit price)
        var fees = 0.0
        var profitLoss = 0.0
        var closeDatetime: LocalDateTime = openDatetime
        val transactions = mutableListOf<Transaction>()

        fun toClosedPosition(symbol: String) = ClosedPosition(
            symbol = symbol,
            entryDatetime = openDatetime,
            exitDatetime = closeDatetime,
            averageEntryPrice = openValue / matchedShares,
            averageExitPrice = closeValue / matchedShares,
            shares = matchedShares,
            fees = fees,
            profitLoss = profitLoss,
            transactions = transactions.toList(),
            direction = direction,
        )
    }

    fun computeClosedPositions(transactions: List<Transaction>): List<ClosedPosition> {
        val result = mutableListOf<ClosedPosition>()

        for ((symbol, symbolTxs) in transactions.groupBy { it.symbol }) {
            val openQueue = ArrayDeque<Lot>()    // open lots on the trip's opening side; empty == flat
            var acc: TradeAccumulator? = null

            // Emit the current trade if it realized anything, then go flat.
            fun flush() {
                acc?.let { if (it.matchedShares > 0.0) result.add(it.toClosedPosition(symbol)) }
                acc = null
            }

            for (tx in symbolTxs.sortedBy { it.datetime }) {
                val txSide = if (tx.action == Action.BUY) TradeDirection.LONG else TradeDirection.SHORT

                // Opening from flat: the order's side sets the trip direction.
                if (acc == null) acc = TradeAccumulator(txSide, tx.datetime)
                var current = acc!!
                current.transactions.add(tx)

                if (txSide == current.direction) {
                    // Same side as the position: scale in.
                    openQueue.addLast(Lot(tx.price, tx.shares, tx.shares, tx.fees))
                    continue
                }

                // Opposite side: close/reduce the position, matching FIFO against open lots.
                var remaining = tx.shares
                val closeFeePerShare = tx.fees / tx.shares
                val sign = if (current.direction == TradeDirection.LONG) 1.0 else -1.0

                while (remaining > 0.0 && openQueue.isNotEmpty()) {
                    val lot = openQueue.first()
                    val matched = minOf(remaining, lot.remainingShares)
                    val openFee = lot.totalFee * (matched / lot.totalShares)
                    val closeFee = closeFeePerShare * matched
                    val feeMatch = openFee + closeFee

                    current.matchedShares += matched
                    current.openValue += lot.price * matched
                    current.closeValue += tx.price * matched
                    current.fees += feeMatch
                    current.profitLoss += sign * (tx.price - lot.price) * matched - feeMatch
                    current.closeDatetime = tx.datetime

                    lot.remainingShares -= matched
                    remaining -= matched
                    if (lot.remainingShares <= 0.0) openQueue.removeFirst()
                }

                // Position is flat again: close out this trade.
                if (openQueue.isEmpty()) {
                    flush()
                    // Flip: leftover closing shares open a new position in the opposite direction.
                    // Their share of this order's fee becomes the new lot's opening fee.
                    if (remaining > 0.0) {
                        acc = TradeAccumulator(txSide, tx.datetime)
                        current = acc!!
                        current.transactions.add(tx)
                        openQueue.addLast(Lot(tx.price, remaining, remaining, closeFeePerShare * remaining))
                    }
                }
            }

            // End of data: emit the realized portion of any still-open position.
            flush()
        }

        return result
    }
}
