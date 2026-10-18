package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.tradezero.TradeZeroExternalIdFactory
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import io.earlisreal.ejournal.domain.analytics.unambiguousEasternInstant

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

    override fun parse(content: ByteArray, portfolioId: Long): ParseResult {
        val externalIds = TradeZeroExternalIdFactory()
        val transactions = mutableListOf<Transaction>()
        var unparsed = 0
        for (line in content.decodeToString().lines().drop(1)) {
            if (line.isBlank()) continue
            val tx = runCatching { parseRow(parseCsvLine(line), portfolioId, externalIds) }.getOrNull()
            if (tx != null) transactions += tx else unparsed++
        }
        return ParseResult(transactions, SkipSummary(unparsed = unparsed))
    }

    private fun parseRow(c: List<String>, portfolioId: Long, externalIds: TradeZeroExternalIdFactory): Transaction {
        val symbol = c[SYMBOL].trim()
        val action = if (c[SIDE].trim().equals("B", ignoreCase = true)) Action.BUY else Action.SELL
        val shares = c[QTY].toNumber()
        val price = c[PRICE].toNumber()
        val (mm, dd, yyyy) = c[TRADE_DATE].trim().split("/")
        val date = LocalDate(yyyy.toInt(), mm.toInt(), dd.toInt())
        val time = runCatching { LocalTime.parse(c[EXEC_TIME].trim()) }.getOrNull()
        val offsetInstant = runCatching { Instant.parse("${date}T${c[EXEC_TIME].trim()}") }.getOrNull()
        val datetime = offsetInstant?.toLocalDateTime(TimeZone.of("America/New_York"))
            ?: LocalDateTime(date, time ?: LocalTime(0, 0))
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
            verifiedExecutionInstant = offsetInstant ?: time?.let { unambiguousEasternInstant(datetime) },
            verifiedMarketDate = date,
        )
    }

    // Strips thousands separators (a quoted "1,500" survives CSV tokenization as "1,500"), matching
    // the moomoo parser; without this such a value would throw and the whole row be silently dropped.
    private fun String.toNumber(): Double = trim().replace(",", "").toDouble()
    private fun String?.toNumberOrZero(): Double =
        this?.trim()?.takeIf { it.isNotEmpty() }?.replace(",", "")?.toDouble() ?: 0.0

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
