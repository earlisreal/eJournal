package io.earlisreal.ejournal.domain.model

import kotlinx.datetime.LocalDateTime

data class Transaction(
    val id: Long,
    val portfolioId: Long,
    val symbol: String,
    val datetime: LocalDateTime,
    val action: Action,
    val price: Double,
    val shares: Double,
    val fees: Double
)
