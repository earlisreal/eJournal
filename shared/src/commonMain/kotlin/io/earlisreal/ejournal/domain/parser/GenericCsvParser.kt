package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime

class GenericCsvParser : TransactionParser {
    override val brokerName = "Generic CSV"
    override val supportedExtensions = listOf("csv")

    override fun parse(content: ByteArray, portfolioId: Long): List<Transaction> {
        val lines = content.decodeToString().lines().drop(1).filter { it.isNotBlank() }
        return lines.mapNotNull { line ->
            runCatching {
                val parts = line.split(",")
                Transaction(
                    id = 0L,
                    portfolioId = portfolioId,
                    symbol = parts[1].trim(),
                    datetime = LocalDateTime.parse(parts[0].trim()),
                    action = Action.valueOf(parts[2].trim().uppercase()),
                    price = parts[3].trim().toDouble(),
                    shares = parts[4].trim().toDouble(),
                    fees = parts[5].trim().toDouble()
                )
            }.getOrNull()
        }
    }
}
