package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime

/**
 * eToro account-statement semantics, kept platform-agnostic so they can be unit-tested with plain row
 * data (the XLSX byte-reading layer lives in jvmMain). The source is the statement's "Account Activity"
 * sheet — a chronological ledger where `Open Position` rows are entries and `Position closed` rows are
 * exits; everything else (deposits, withdrawals, copy start/stop, overnight fees, adjustments, dividends)
 * is a non-trade ledger entry we skip and tally.
 *
 * Notes on the eToro model:
 *  - Dates are `DD/MM/YYYY HH:MM:SS` (this is an eToro AUS export — day first, not the US month-first form
 *    the shared CSV helpers assume).
 *  - `Amount` is the position's USD value and `Units / Contracts` the quantity, so per-share price is
 *    `Amount / Units`.
 *  - eToro charges no per-trade commission (its cost is the spread, baked into the open/close rate), so
 *    trade `fees` are 0; overnight financing fees arrive as their own ledger rows and are counted as
 *    non-trade. Closed positions are recomputed downstream by FIFO per symbol, so position-id linkage and
 *    the separate Closed Positions sheet are intentionally not used here.
 *  - Positions are assumed LONG (`Open Position` -> BUY, `Position closed` -> SELL). The Account Activity
 *    sheet carries no long/short flag (only the Closed Positions sheet does), so CFD short positions —
 *    which this importer does not yet support — would be mapped with inverted direction.
 */

internal const val ETORO_ACTIVITY_SHEET = "Account Activity"
private const val ETORO_CLOSED_POSITIONS_SHEET = "Closed Positions"

/** Recognizes an eToro statement by its two signature sheet names (case-insensitive). */
internal fun isEtoroStatement(sheetNames: List<String>): Boolean {
    val lower = sheetNames.map { it.trim().lowercase() }
    return ETORO_ACTIVITY_SHEET.lowercase() in lower && ETORO_CLOSED_POSITIONS_SHEET.lowercase() in lower
}

/** Parses an eToro `DD/MM/YYYY HH:MM:SS` (time optional) cell to a [LocalDateTime], or null if malformed. */
internal fun parseEtoroDateTime(raw: String): LocalDateTime? {
    val parts = raw.trim().split(" ").filter { it.isNotBlank() }
    val dmy = parts.getOrNull(0)?.split("/") ?: return null
    if (dmy.size != 3) return null
    val dd = dmy[0].toIntOrNull() ?: return null
    val mm = dmy[1].toIntOrNull() ?: return null
    val yyyy = dmy[2].toIntOrNull() ?: return null
    if (dd !in 1..31 || mm !in 1..12) return null

    val time = parts.getOrNull(1)?.split(":")
    val hh = time?.getOrNull(0)?.toIntOrNull() ?: 0
    val mi = time?.getOrNull(1)?.toIntOrNull() ?: 0
    val ss = time?.getOrNull(2)?.toIntOrNull() ?: 0
    if (hh !in 0..23 || mi !in 0..59 || ss !in 0..59) return null

    val iso = "${yyyy.pad(4)}-${mm.pad(2)}-${dd.pad(2)}T${hh.pad(2)}:${mi.pad(2)}:${ss.pad(2)}"
    return runCatching { LocalDateTime.parse(iso) }.getOrNull()
}

private fun Int.pad(width: Int) = toString().padStart(width, '0')

/**
 * The instrument's base symbol: the `Details` cell with its `/USD` quote suffix, stray space, and
 * eToro's `.US` exchange suffix removed. eToro tags some US tickers as `TICKER.US` (e.g. "SNX.US",
 * "IP.US"); Yahoo and Alpaca only know the bare ticker, so the suffix must go or market-data sync
 * 404s the symbol. (Only the US exchange is handled — this app's stock market data is US-only.)
 */
private fun etoroSymbol(details: String): String =
    details.substringBeforeLast("/").trim().removeSuffix(".US")

/**
 * Whether a row's "Asset type" cell denotes crypto. eToro labels crypto positions `CFD` in this AUS
 * export (real equities are `Stocks`); other regions use a literal `Crypto`, so we accept both. NB:
 * `CFD` literally means contract-for-difference, which in general eToro can also be a leveraged stock /
 * index / commodity / forex position — but this account only ever trades crypto as CFD, so the mapping
 * holds. Refine with a known-crypto-symbol check when crypto market data lands.
 */
internal fun etoroRowIsCrypto(raw: String?): Boolean {
    val v = raw?.trim()?.lowercase().orEmpty()
    return v == "cfd" || "crypto" in v
}

/**
 * Maps the rows of an "Account Activity" sheet (header row included, anywhere in [rows]) to BUY/SELL
 * [Transaction]s, keeping only trades whose asset class matches [market] — eToro statements mix stocks
 * and crypto, but portfolios are single-asset, so crypto trades belong to a crypto portfolio and stock
 * trades to a stocks portfolio. Rows recognized but not emitted are tallied in [ParseResult.skipped]:
 * non-trade ledger entries as `nonTrade`, trades for the other asset class as `offMarket`, and trade
 * rows whose figures don't parse as `unparsed`.
 */
internal fun parseEtoroActivity(
    rows: List<List<String>>,
    portfolioId: Long,
    market: Market = Market.US_STOCKS,
): ParseResult {
    val headerIdx = rows.indexOfFirst { r ->
        val norm = r.map { it.trim().lowercase() }
        "type" in norm && "details" in norm && norm.any { it.startsWith("units") }
    }
    if (headerIdx < 0) return ParseResult(emptyList())

    val columns = rows[headerIdx].map { it.trim() }
    val index = columns.withIndex().associate { (i, name) -> name.trim().lowercase() to i }

    val wantCrypto = market == Market.CRYPTO
    val keys = NaturalKeyFactory("etoro")
    val txns = mutableListOf<Transaction>()
    var nonTrade = 0
    var offMarket = 0
    var unparsed = 0

    for (row in rows.drop(headerIdx + 1)) {
        if (row.all { it.isBlank() }) continue
        val action = when (row.field(index, "Type")?.lowercase()) {
            "open position" -> Action.BUY
            "position closed" -> Action.SELL
            else -> { nonTrade++; continue }
        }
        // Single-asset portfolios: keep only rows matching the target market; tally the rest as offMarket.
        if (etoroRowIsCrypto(row.field(index, "Asset type")) != wantCrypto) { offMarket++; continue }
        val symbol = etoroSymbol(row.field(index, "Details").orEmpty())
        val amount = cleanMoney(row.field(index, "Amount"))
        val units = cleanMoney(row.field(index, "Units / Contracts"))
        val datetime = parseEtoroDateTime(row.field(index, "Date").orEmpty())
        if (symbol.isEmpty() || amount == null || units == null || units == 0.0 || datetime == null) {
            unparsed++
            continue
        }
        txns += Transaction(
            id = 0L,
            portfolioId = portfolioId,
            symbol = symbol,
            datetime = datetime,
            action = action,
            price = amount / units,
            shares = units,
            fees = 0.0,
            externalId = keys.create(symbol, datetime, action, units),
        )
    }
    return ParseResult(txns, SkipSummary(nonTrade = nonTrade, unparsed = unparsed, offMarket = offMarket))
}
