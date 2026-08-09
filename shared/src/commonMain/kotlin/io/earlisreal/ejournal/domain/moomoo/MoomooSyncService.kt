package io.earlisreal.ejournal.domain.moomoo

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.background.TaskHandle
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.broker.BrokerSyncDetail
import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.broker.BrokerSyncService
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

fun interface MoomooRequestGate {
    suspend fun awaitPermit()
}

/** A three-second interval is the smallest implementation that never exceeds 10 requests/30 seconds. */
class MoomooRateLimiter(
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val sleep: suspend (Long) -> Unit = { delay(it.milliseconds) },
    private val intervalMillis: Long = 3_000,
) : MoomooRequestGate {
    private val mutex = Mutex()
    private var lastPermit: Long? = null

    override suspend fun awaitPermit() = mutex.withLock {
        val previous = lastPermit
        if (previous != null) {
            val wait = (previous + intervalMillis - nowMillis()).coerceAtLeast(0)
            if (wait > 0) sleep(wait)
        }
        lastPermit = maxOf(nowMillis(), previous?.plus(intervalMillis) ?: Long.MIN_VALUE)
    }
}

data class MoomooWindow(val from: LocalDate, val to: LocalDate)

fun moomooWindows(from: LocalDate, to: LocalDate): List<MoomooWindow> {
    if (from > to) return emptyList()
    val result = mutableListOf<MoomooWindow>()
    var cursor = from
    while (cursor <= to) {
        val end = minOf(to, cursor.plus(89, DateTimeUnit.DAY))
        result += MoomooWindow(cursor, end)
        cursor = end.plus(1, DateTimeUnit.DAY)
    }
    return result
}

class MoomooSyncService(
    private val client: MoomooClient,
    private val transactionRepository: TransactionRepository,
    private val tracker: BackgroundTaskTracker,
    private val portfolioRepository: PortfolioRepository,
    private val portfolioSettings: PortfolioSettingsRepository,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
    private val requestGateFactory: () -> MoomooRequestGate = { MoomooRateLimiter() },
) : BrokerSyncService {
    private val syncMutex = Mutex()

    override val brokerId = "moomoo"
    override val displayName = "Moomoo OpenD"
    override val autoSyncSettingKey = MoomooSettings.AUTO_SYNC_ON_STARTUP
    override val autoSyncDefault = MoomooSettings.AUTO_SYNC_DEFAULT

    // The persisted configuration is suspend-only; syncIncremental performs the authoritative check.
    override fun isConfigured(portfolio: Portfolio): Boolean = portfolio.broker == Broker.MOOMOO
    override fun supportsMarket(market: Market): Boolean = market == Market.US_STOCKS

    override suspend fun syncIncremental(portfolioId: Long): BrokerSyncOutcome =
        syncMutex.withLock { syncLocked(portfolioId) }

    private suspend fun syncLocked(portfolioId: Long): BrokerSyncOutcome {
        val portfolio = portfolioRepository.getById(portfolioId) ?: return BrokerSyncOutcome.NotConfigured
        if (portfolio.broker != Broker.MOOMOO || portfolio.market != Market.US_STOCKS) {
            return BrokerSyncOutcome.NotConfigured
        }
        val config = portfolioSettings.getMoomooConfig(portfolioId) ?: return BrokerSyncOutcome.NotConfigured
        findBindingConflict(portfolioId, config.source)?.let {
            return BrokerSyncOutcome.AccountAlreadyBound(it.name)
        }

        val handle = tracker.start(TASK_ID, TASK_LABEL, "Connecting to OpenD on 127.0.0.1:${config.port}…")
        var session: MoomooSession? = null
        return try {
            session = when (val opened = client.open(config.port)) {
                is MoomooResult.Failure -> return fail(handle, opened.message)
                is MoomooResult.Success -> opened.value
            }
            val gate = requestGateFactory()
            gate.awaitPermit()
            val accounts = when (val result = session.getAccounts()) {
                is MoomooResult.Failure -> return fail(handle, result.message)
                is MoomooResult.Success -> result.value.eligibleForUsStocks()
            }
            if (accounts.none { it.id == config.accountId }) {
                return fail(handle, "Selected live US Moomoo account is not available in OpenD")
            }

            val lastSource = portfolioSettings.getString(portfolioId, MoomooSettings.LAST_SYNCED_SOURCE)
            val checkpoint = if (lastSource == config.source) {
                portfolioSettings.getString(portfolioId, MoomooSettings.LAST_COMPLETED_DATE)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            } else {
                null
            }
            val start = maxOf(
                INITIAL_DATE,
                checkpoint?.minus(OVERLAP_DAYS, DateTimeUnit.DAY) ?: INITIAL_DATE,
            )
            val end = today()
            var inserted = 0
            val skipped = mutableMapOf<String, Int>()

            for (window in moomooWindows(start, end)) {
                gate.awaitPermit()
                val orders = when (val result = session.getHistoricalOrders(config.accountId, window.from, window.to)) {
                    is MoomooResult.Failure -> return fail(handle, result.message)
                    is MoomooResult.Success -> result.value
                }
                gate.awaitPermit()
                val executions = when (val result = session.getHistoricalExecutions(config.accountId, window.from, window.to)) {
                    is MoomooResult.Failure -> return fail(handle, result.message)
                    is MoomooResult.Success -> result.value
                }

                val mapped = mapOrders(orders, executions, portfolioId, skipped)
                val fees = mutableMapOf<String, Double>()
                for (batch in mapped.chunked(MAX_FEE_IDS)) {
                    gate.awaitPermit()
                    when (val result = session.getOrderFees(config.accountId, batch.map { it.orderId })) {
                        is MoomooResult.Failure -> return fail(handle, result.message)
                        is MoomooResult.Success -> result.value.forEach { fee ->
                            fee.amount?.takeIf(Double::isFinite)?.let { fees[fee.orderId] = it }
                        }
                    }
                }
                val missingFees = mapped.map { it.orderId }.filterNot(fees::containsKey)
                if (missingFees.isNotEmpty()) {
                    return fail(handle, "OpenD did not return an exact fee for ${missingFees.size} order(s)")
                }

                mapped.forEach { row ->
                    val transaction = row.toTransaction(portfolioId, fees.getValue(row.orderId))
                    if (transactionRepository.insert(transaction) != null) inserted++
                }
                // A checkpoint belongs only to a window whose fees and idempotent inserts all completed.
                portfolioSettings.putString(portfolioId, MoomooSettings.LAST_COMPLETED_DATE, window.to.toString())
                portfolioSettings.putString(portfolioId, MoomooSettings.LAST_SYNCED_SOURCE, config.source)
            }

            val detail = BrokerSyncDetail(skipped.filterValues { it > 0 })
            handle.succeed("Imported $inserted new transaction(s)")
            BrokerSyncOutcome.Imported(inserted, detail)
        } catch (error: CancellationException) {
            handle.cancel()
            throw error
        } catch (error: Exception) {
            handle.fail("Moomoo import failed: ${error.message ?: "request failed"}")
            throw error
        } finally {
            runCatching { session?.close() }
        }
    }

    private suspend fun findBindingConflict(portfolioId: Long, source: String): Portfolio? =
        portfolioRepository.getAll().firstOrNull { candidate ->
            candidate.id != portfolioId &&
                (portfolioSettings.getString(candidate.id, MoomooSettings.ACCOUNT_SOURCE) == source ||
                    portfolioSettings.getString(candidate.id, MoomooSettings.LAST_SYNCED_SOURCE) == source)
        }

    private fun fail(handle: TaskHandle, message: String): BrokerSyncOutcome.NetworkError {
        handle.fail("Moomoo OpenD error: $message")
        return BrokerSyncOutcome.NetworkError(message)
    }

    companion object {
        const val TASK_ID = "moomoo-import"
        const val TASK_LABEL = "Moomoo OpenD import"
        const val MAX_FEE_IDS = 400
        const val OVERLAP_DAYS = 3
        val INITIAL_DATE = LocalDate(2018, 1, 1)

        private val OPTION_SYMBOL = Regex("^[A-Z][A-Z0-9.-]{0,9}\\d{6}[CP]\\d{6,}$")

        internal fun mapOrders(
            orders: List<MoomooOrder>,
            executions: List<MoomooExecution>,
            portfolioId: Long,
            skipped: MutableMap<String, Int> = mutableMapOf(),
        ): List<MappedOrder> {
            val byOrder = executions.groupBy { it.orderId }
            return orders.mapNotNull { order ->
                val symbol = MoomooExternalIdFactory.normalizeSymbol(order.symbol)
                val reason = when {
                    order.market != MoomooMarket.US -> "other markets"
                    OPTION_SYMBOL.matches(symbol.uppercase()) -> "options"
                    order.isCombo -> "combo orders"
                    order.isPrediction -> "prediction contracts"
                    order.side != MoomooSide.BUY && order.side != MoomooSide.SELL -> "unsupported sides"
                    order.filledQuantity <= 0.0 -> "zero fills"
                    order.id.isBlank() || symbol.isBlank() || order.createdAt == null || !order.filledQuantity.isFinite() -> "malformed rows"
                    else -> null
                }
                if (reason != null) {
                    skipped.increment(reason)
                    return@mapNotNull null
                }

                val fills = byOrder[order.id].orEmpty().filter { it.quantity > 0.0 }
                val malformed = fills.isEmpty() || fills.any {
                    it.market != MoomooMarket.US ||
                        it.side != order.side ||
                        MoomooExternalIdFactory.normalizeSymbol(it.symbol) != symbol ||
                        it.executedAt == null ||
                        !it.quantity.isFinite() || !it.price.isFinite() || it.price <= 0.0
                }
                if (malformed) {
                    skipped.increment("malformed rows")
                    return@mapNotNull null
                }
                val quantity = fills.sumOf { it.quantity }
                if (!quantity.isFinite() || quantity <= 0.0) {
                    skipped.increment("zero fills")
                    return@mapNotNull null
                }
                val tolerance = QUANTITY_TOLERANCE * maxOf(1.0, abs(order.filledQuantity), abs(quantity))
                if (abs(quantity - order.filledQuantity) > tolerance) {
                    skipped.increment("malformed rows")
                    return@mapNotNull null
                }
                MappedOrder(
                    orderId = order.id,
                    symbol = symbol,
                    orderCreatedAt = order.createdAt!!,
                    executedAt = fills.mapNotNull { it.executedAt }.minOrNull()!!,
                    action = if (order.side == MoomooSide.BUY) Action.BUY else Action.SELL,
                    quantity = order.filledQuantity,
                    averagePrice = fills.sumOf { it.quantity * it.price } / quantity,
                )
            }
        }

        private const val QUANTITY_TOLERANCE = 1e-9
    }
}

internal data class MappedOrder(
    val orderId: String,
    val symbol: String,
    val orderCreatedAt: LocalDateTime,
    val executedAt: LocalDateTime,
    val action: Action,
    val quantity: Double,
    val averagePrice: Double,
) {
    fun toTransaction(portfolioId: Long, fee: Double) = Transaction(
        id = 0,
        portfolioId = portfolioId,
        symbol = symbol,
        datetime = executedAt,
        action = action,
        price = averagePrice,
        shares = quantity,
        fees = fee,
        externalId = MoomooExternalIdFactory.create(symbol, orderCreatedAt, action, quantity),
    )
}

private fun MutableMap<String, Int>.increment(reason: String) {
    this[reason] = getOrElse(reason) { 0 } + 1
}
