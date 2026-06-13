package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime

object FifoMatcher {

    private data class BuyLot(
        val datetime: LocalDateTime,
        val price: Double,
        var remainingShares: Double,
        val totalShares: Double,
        val totalFee: Double,
        val originalTx: Transaction,
    )

    fun computeClosedPositions(transactions: List<Transaction>): List<ClosedPosition> {
        val result = mutableListOf<ClosedPosition>()
        val bySymbol = transactions.groupBy { it.symbol }

        for ((symbol, symbolTxs) in bySymbol) {
            val buyQueue = ArrayDeque<BuyLot>()

            for (tx in symbolTxs.sortedBy { it.datetime }) {
                when (tx.action) {
                    Action.BUY -> buyQueue.addLast(
                        BuyLot(tx.datetime, tx.price, tx.shares, tx.shares, tx.fees, tx)
                    )
                    Action.SELL -> {
                        var remaining = tx.shares
                        val exitFeePerShare = tx.fees / tx.shares

                        while (remaining > 0.0 && buyQueue.isNotEmpty()) {
                            val lot = buyQueue.first()
                            val matched = minOf(remaining, lot.remainingShares)
                            val entryFee = lot.totalFee * (matched / lot.totalShares)
                            val exitFee = exitFeePerShare * matched
                            val totalFees = entryFee + exitFee

                            result.add(
                                ClosedPosition(
                                    symbol = symbol,
                                    entryDatetime = lot.datetime,
                                    exitDatetime = tx.datetime,
                                    averageEntryPrice = lot.price,
                                    averageExitPrice = tx.price,
                                    shares = matched,
                                    fees = totalFees,
                                    profitLoss = (tx.price - lot.price) * matched - totalFees,
                                    transactions = listOf(lot.originalTx, tx),
                                )
                            )

                            lot.remainingShares -= matched
                            remaining -= matched
                            if (lot.remainingShares <= 0.0) buyQueue.removeFirst()
                        }
                    }
                }
            }
        }

        return result
    }
}
