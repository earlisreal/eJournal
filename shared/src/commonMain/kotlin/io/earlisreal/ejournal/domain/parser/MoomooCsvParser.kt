package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime

/**
 * Parses moomoo's "History" order export. The file is order-level (one row per order), so a single
 * order that filled across several executions appears as one main row plus blank-`Side` continuation
 * rows. We read the whole-order quantity and average price from `Filled@Avg Price` and the aggregated
 * fees from `Total`, emitting one transaction per filled order and skipping continuation rows entirely.
 * Cancelled/failed orders (no fill, `Filled@Avg Price` = `0@0.00`) are skipped.
 *
 * moomoo timestamps are Eastern wall-clock ("Jun 8, 2026 06:51:05 ET"); we store them as-is, matching
 * how the TradeZero API sync stores Eastern wall-clock LocalDateTimes.
 */
class MoomooCsvParser : TransactionParser {
    override val brokerName = "moomoo"
    override val supportedExtensions = listOf("csv")

    override fun detect(content: ByteArray): Boolean {
        val header = content.decodeToString().lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        return header.contains("Filled@Avg Price")
    }

    override fun parse(content: ByteArray, portfolioId: Long): ParseResult {
        val transactions = mutableListOf<Transaction>()
        var nonTrade = 0
        for (line in content.decodeToString().lines().drop(1)) {
            if (line.isBlank()) continue
            val c = runCatching { parseCsvLine(line) }.getOrNull() ?: continue
            // continuation row (blank Side): its fill is already in the order's average — skip, do not count.
            if (c.getOrNull(SIDE)?.trim().isNullOrEmpty()) continue
            val tx = runCatching { parseRow(c, portfolioId) }.getOrNull()
            if (tx != null) transactions += tx else nonTrade++ // cancelled/failed/unparseable order
        }
        return ParseResult(transactions, SkipSummary(nonTrade = nonTrade))
    }

    private fun parseRow(c: List<String>, portfolioId: Long): Transaction? {
        val side = c[SIDE].trim()
        if (side.isEmpty()) return null // continuation row — its fill is already in the order's average

        val filledAvg = c[FILLED_AVG].trim()
        if (filledAvg.isEmpty()) return null
        val (qtyPart, pricePart) = filledAvg.split("@").let { it[0] to it[1] }
        val shares = qtyPart.toNumber()
        if (shares <= 0.0) return null // cancelled/failed order ("0@0.00")
        val price = pricePart.toNumber()

        val symbol = c[SYMBOL].trim()
        val action = if (side.equals("Buy", ignoreCase = true)) Action.BUY else Action.SELL
        val orderTime = parseEasternDateTime(c[ORDER_TIME])
        val fillTime = c[FILL_TIME].trim()
        val datetime = if (fillTime.isNotEmpty()) parseEasternDateTime(fillTime) else orderTime
        val fees = c.getOrNull(TOTAL)?.trim()?.takeIf { it.isNotEmpty() }?.toNumber() ?: 0.0

        return Transaction(
            id = 0L,
            portfolioId = portfolioId,
            symbol = symbol,
            datetime = datetime,
            action = action,
            price = price,
            shares = shares,
            fees = fees,
            // Order Time uniquely identifies an order placement, so it makes re-imports idempotent.
            externalId = "moomoo:$symbol:$orderTime:${action.name}:$shares",
        )
    }

    // Strips thousands separators ("1,127") before parsing.
    private fun String.toNumber(): Double = trim().replace(",", "").toDouble()

    // "Jun 8, 2026 06:51:05 ET" -> LocalDateTime. Reformat into ISO so we don't depend on a
    // particular kotlinx-datetime constructor overload; the trailing zone token (ET) is ignored.
    private fun parseEasternDateTime(raw: String): LocalDateTime {
        val p = raw.trim().split(" ").filter { it.isNotBlank() }
        val month = MONTHS.getValue(p[0])
        val day = p[1].removeSuffix(",").toInt()
        val year = p[2].toInt()
        val time = p[3] // "HH:mm:ss"
        val iso = "${year.toString().padStart(4, '0')}-" +
            "${month.toString().padStart(2, '0')}-" +
            "${day.toString().padStart(2, '0')}T$time"
        return LocalDateTime.parse(iso)
    }

    private companion object {
        const val SIDE = 0
        const val SYMBOL = 1
        const val FILLED_AVG = 7
        const val ORDER_TIME = 8
        const val FILL_TIME = 21
        const val TOTAL = 31

        val MONTHS = mapOf(
            "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
            "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12,
        )
    }
}
