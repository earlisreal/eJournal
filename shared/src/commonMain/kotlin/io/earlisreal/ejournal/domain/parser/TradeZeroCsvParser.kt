package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.tradezero.TradeZeroExternalIdFactory
import kotlinx.datetime.LocalDateTime

/**
 * Parses TradeZero's "TradeHistory" CSV export — fill-level (one row per execution), primarily for
 * backfilling history older than the API window. External ids come from the shared
 * [TradeZeroExternalIdFactory], so a fill imported from this CSV and the same fill pulled by the live
 * API sync produce the same id and dedup against each other.
 *
 * Fees are the sum of all fee columns (Comm + SEC + TAF + NSCC + Nasdaq + ECN Remove + ECN Add),
 * which equals |Net − Gross Proceeds|.
 */
class TradeZeroCsvParser : TransactionParser {
    override val brokerName = "TradeZero (CSV)"
    override val supportedExtensions = listOf("csv")

    override fun detect(content: ByteArray): Boolean {
        val header = content.decodeToString().lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        return header.startsWith("Account,T/D,S/D,Currency,Type,Side,Symbol")
    }

    override fun parse(content: ByteArray, portfolioId: Long): List<Transaction> {
        val externalIds = TradeZeroExternalIdFactory()
        return content.decodeToString().lines().drop(1).mapNotNull { line ->
            if (line.isBlank()) null
            else runCatching { parseRow(parseCsvLine(line), portfolioId, externalIds) }.getOrNull()
        }
    }

    private fun parseRow(c: List<String>, portfolioId: Long, externalIds: TradeZeroExternalIdFactory): Transaction {
        val symbol = c[SYMBOL].trim()
        val action = if (c[SIDE].trim().equals("B", ignoreCase = true)) Action.BUY else Action.SELL
        val shares = c[QTY].toNumber()
        val price = c[PRICE].toNumber()
        val datetime = parseDateTime(c[TRADE_DATE], c[EXEC_TIME])
        val fees = FEE_COLUMNS.sumOf { c.getOrNull(it).toNumberOrZero() }

        return Transaction(
            id = 0L,
            portfolioId = portfolioId,
            symbol = symbol,
            datetime = datetime,
            action = action,
            price = price,
            shares = shares,
            fees = fees,
            externalId = externalIds.create(symbol, datetime, action, shares),
        )
    }

    // Strips thousands separators (a quoted "1,500" survives CSV tokenization as "1,500"), matching
    // the moomoo parser; without this such a value would throw and the whole row be silently dropped.
    private fun String.toNumber(): Double = trim().replace(",", "").toDouble()
    private fun String?.toNumberOrZero(): Double =
        this?.trim()?.takeIf { it.isNotEmpty() }?.replace(",", "")?.toDouble() ?: 0.0

    // T/D "MM/DD/YYYY" + Exec Time "HH:mm:ss" -> LocalDateTime (Eastern wall-clock), via ISO.
    private fun parseDateTime(date: String, time: String): LocalDateTime {
        val (mm, dd, yyyy) = date.trim().split("/")
        val iso = "${yyyy.padStart(4, '0')}-${mm.padStart(2, '0')}-${dd.padStart(2, '0')}T${time.trim()}"
        return LocalDateTime.parse(iso)
    }

    private companion object {
        const val SIDE = 5
        const val SYMBOL = 6
        const val QTY = 7
        const val PRICE = 8
        const val EXEC_TIME = 9
        const val TRADE_DATE = 1
        val FEE_COLUMNS = 10..16 // Comm, SEC, TAF, NSCC, Nasdaq, ECN Remove, ECN Add
    }
}
