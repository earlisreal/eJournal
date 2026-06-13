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
)
