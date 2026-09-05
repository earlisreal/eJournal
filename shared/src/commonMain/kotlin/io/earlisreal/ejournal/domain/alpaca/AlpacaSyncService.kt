package io.earlisreal.ejournal.domain.alpaca

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.data.repository.AlpacaBrokerSecrets
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.broker.BrokerFeeSummary
import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.broker.BrokerSyncService
import io.earlisreal.ejournal.domain.broker.describeImport
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val EASTERN = TimeZone.of("America/New_York")
private val UTC = TimeZone.UTC

class AlpacaSyncService(
    private val client: AlpacaBrokerClient,
    private val transactionRepository: TransactionRepository,
    private val tracker: BackgroundTaskTracker,
    private val portfolioRepository: PortfolioRepository,
    private val portfolioSettings: PortfolioSettingsRepository,
    private val credentialsRepository: CredentialsRepository,
    private val now: () -> Instant = { Clock.System.now() },
) : BrokerSyncService {

    private val syncMutex = Mutex()

    override val brokerId: String = "alpaca"
    override val displayName: String = "Alpaca"

    override fun isConfigured(portfolio: Portfolio): Boolean =
        portfolio.broker == Broker.ALPACA &&
            credentialsRepository.getPortfolioBrokerCredentials(portfolio.credentialRef) is AlpacaBrokerSecrets

    override fun supportsMarket(market: Market): Boolean = market == Market.US_STOCKS

    override suspend fun syncIncremental(portfolioId: Long): BrokerSyncOutcome =
        syncMutex.withLock { syncIncrementalLocked(portfolioId) }

    private suspend fun syncIncrementalLocked(portfolioId: Long): BrokerSyncOutcome {
        val portfolio = portfolioRepository.getById(portfolioId) ?: return BrokerSyncOutcome.NotConfigured
        if (portfolio.broker != Broker.ALPACA) return BrokerSyncOutcome.NotConfigured
        val credentials = credentialsFor(portfolio) ?: return BrokerSyncOutcome.NotConfigured
        val until = now()
        val untilDate = until.toLocalDateTime(UTC).date
        val handle = tracker.start(TASK_ID, TASK_LABEL, "Fetching Alpaca fills and fees…")

        return try {
            val connection = when (val result = client.testConnection(credentials)) {
                is AlpacaConnectionResult.Connected -> result
                AlpacaConnectionResult.InvalidCredentials -> {
                    handle.fail("Invalid Alpaca credentials — update this portfolio's broker configuration")
                    return BrokerSyncOutcome.InvalidCredentials
                }
                is AlpacaConnectionResult.NetworkError -> {
                    handle.fail("Alpaca network error: ${result.message}")
                    return BrokerSyncOutcome.NetworkError(result.message)
                }
            }
            val source = AlpacaSettings.source(connection.environment, connection.account.id)
            findBindingConflict(portfolioId, source)?.let { boundPortfolio ->
                val message =
                    "This Alpaca ${connection.environment.label} account is already linked to portfolio \"${boundPortfolio.name}\""
                handle.fail(message)
                return BrokerSyncOutcome.AccountAlreadyBound(boundPortfolio.name)
            }

            val lastSyncedSource = portfolioSettings.getString(portfolioId, AlpacaSettings.LAST_SYNCED_SOURCE)
            val fullHistory = lastSyncedSource != source ||
                portfolioSettings.getString(portfolioId, AlpacaSettings.FEE_ALLOCATION_VERSION) !=
                AlpacaSettings.FEE_ALLOCATION_VERSION_VALUE
            val fromDate = if (fullHistory) {
                null
            } else {
                untilDate.minus(FEE_REPLAY_DAYS, DateTimeUnit.DAY)
            }
            val after = fromDate?.atStartOfDayIn(UTC)?.minus(1.seconds)

            when (val result = client.fetchFills(credentials, portfolioId, after, until)) {
                is AlpacaFetchResult.Success -> {
                    check(result.account.id == connection.account.id) {
                        "Alpaca account changed during synchronization"
                    }
                    val inserted = result.transactions.count { transactionRepository.insert(it) != null }
                    val feeSummary = reconcileFees(
                        portfolioId = portfolioId,
                        source = source,
                        result = result,
                        fromDate = fromDate,
                        untilDate = untilDate,
                    )

                    // Cursor and version state are written only after the fee replacement commits.
                    portfolioSettings.putString(portfolioId, AlpacaSettings.LAST_SYNCED_AT, until.toString())
                    portfolioSettings.putString(portfolioId, AlpacaSettings.LAST_SYNCED_SOURCE, source)
                    if (fullHistory) {
                        portfolioSettings.putString(
                            portfolioId,
                            AlpacaSettings.FEE_ALLOCATION_VERSION,
                            AlpacaSettings.FEE_ALLOCATION_VERSION_VALUE,
                        )
                    }

                    val detail = result.detail.copy(
                        feeSummary = feeSummary.takeIf { result.fills.isNotEmpty() || result.fees.isNotEmpty() }
                            ?: result.detail.feeSummary,
                    )
                    handle.succeed(detail.describeImport(inserted))
                    BrokerSyncOutcome.Imported(inserted, detail)
                }
                AlpacaFetchResult.InvalidCredentials -> {
                    handle.fail("Invalid Alpaca credentials — update this portfolio's broker configuration")
                    BrokerSyncOutcome.InvalidCredentials
                }
                is AlpacaFetchResult.NetworkError -> {
                    handle.fail("Alpaca network error: ${result.message}")
                    BrokerSyncOutcome.NetworkError(result.message)
                }
            }
        } catch (e: CancellationException) {
            handle.cancel()
            throw e
        } catch (e: Exception) {
            handle.fail("Alpaca import failed: ${e.message ?: "request failed"}")
            throw e
        }
    }

    private suspend fun reconcileFees(
        portfolioId: Long,
        source: String,
        result: AlpacaFetchResult.Success,
        fromDate: LocalDate?,
        untilDate: LocalDate,
    ): BrokerFeeSummary {
        val transactions = transactionRepository.getByPortfolio(portfolioId)
        val prefix = "alpaca:$source:"
        val candidates = transactions.filter { transaction ->
            val externalId = transaction.externalId ?: return@filter false
            if (!externalId.startsWith(prefix)) return@filter false
            val date = transaction.alpacaUtcDate() ?: return@filter false
            date <= untilDate && (fromDate == null || date >= fromDate)
        }
        val updates = candidates.associate { it.externalId!! to 0.0 }.toMutableMap()
        val fillsByDate = result.fills.groupBy { it.transactionTime?.toLocalDateTime(UTC)?.date }
        val candidatesByDate = candidates.groupBy { it.alpacaUtcDate()!! }
        val globallyContaminated = result.fills.any { !it.isEligible() && it.transactionTime == null }
        val contaminatedDates = result.fills.asSequence()
            .filter { !it.isEligible() }
            .mapNotNull { it.transactionTime?.toLocalDateTime(UTC)?.date }
            .toSet()

        val warnings = mutableMapOf<String, Int>()
        var allocated = 0.0
        var unapplied = 0.0
        val buckets = linkedMapOf<FeeBucket, Double>()

        result.fees.forEach { fee ->
            val feeDate = fee.feeDate
            if (feeDate != null && (feeDate > untilDate || fromDate != null && feeDate < fromDate)) return@forEach
            val amount = fee.netAmount
            val journalAmount = amount?.takeIf { it.isFinite() }?.let { -it }
            val subtype = fee.subtype?.trim()?.uppercase()
            val invalidReason = when {
                fee.id.isNullOrBlank() || subtype.isNullOrBlank() || feeDate == null || fee.createdAt == null ->
                    "invalid fee rows"
                !fee.status.equals("executed", ignoreCase = true) -> "non-final fee rows"
                !fee.currency.equals("USD", ignoreCase = true) -> "non-USD fee rows"
                amount == null || !amount.isFinite() -> "non-finite fee rows"
                subtype !in SUPPORTED_FEE_TYPES -> "unknown fee subtypes"
                else -> null
            }
            if (invalidReason != null) {
                warnings.increment(invalidReason)
                if (journalAmount != null) unapplied += journalAmount
                return@forEach
            }
            val key = FeeBucket(feeDate!!, subtype!!)
            buckets[key] = (buckets[key] ?: 0.0) + journalAmount!!
        }

        buckets.forEach { (bucket, total) ->
            val fills = fillsByDate[bucket.date].orEmpty()
            val contamination = globallyContaminated || bucket.date in contaminatedDates
            if (contamination) {
                warnings.increment("contaminated fee dates")
                unapplied += total
                return@forEach
            }
            if (fills.isEmpty()) {
                warnings.increment("fee dates without complete fills")
                unapplied += total
                return@forEach
            }

            val weighted = candidatesByDate[bucket.date].orEmpty().mapNotNull { transaction ->
                allocationWeight(transaction, bucket.subtype)?.let { transaction to it }
            }
            if (weighted.isEmpty()) {
                warnings.increment("fee buckets without eligible transactions")
                unapplied += total
                return@forEach
            }
            val allocations = allocate(total, weighted)
            allocations.forEach { (externalId, fee) -> updates[externalId] = updates.getValue(externalId) + fee }
            allocated += total
        }

        transactionRepository.replaceFeesByExternalId(portfolioId, updates)
        return BrokerFeeSummary(allocatedFees = allocated, unappliedFees = unapplied, warnings = warnings)
    }

    private fun allocationWeight(transaction: Transaction, subtype: String): Double? {
        if (!transaction.shares.isFinite() || transaction.shares <= 0.0) return null
        return when (subtype) {
            "REG" -> if (transaction.action == Action.SELL && transaction.price.isFinite() && transaction.price > 0.0) {
                transaction.shares * transaction.price
            } else {
                null
            }
            "TAF" -> if (transaction.action == Action.SELL) transaction.shares else null
            "CAT" -> if (transaction.action == Action.BUY || transaction.action == Action.SELL) {
                transaction.shares
            } else {
                null
            }
            else -> null
        }?.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun allocate(total: Double, weighted: List<Pair<Transaction, Double>>): Map<String, Double> {
        val weightTotal = weighted.sumOf { it.second }
        val raw = weighted.map { (transaction, weight) ->
            transaction.externalId!! to total * weight / weightTotal
        }
        val largest = raw.minWithOrNull(compareBy<Pair<String, Double>> { -abs(it.second) }.thenBy { it.first })!!
        val remainder = total - raw.sumOf { it.second }
        return raw.associate { (externalId, fee) ->
            externalId to if (externalId == largest.first) fee + remainder else fee
        }
    }

    private fun Transaction.alpacaUtcDate(): LocalDate? =
        runCatching { datetime.toInstant(EASTERN).toLocalDateTime(UTC).date }.getOrNull()

    private fun AlpacaFillActivity.isEligible(): Boolean =
        assetClass == AlpacaFillAssetClass.US_EQUITY &&
            !id.isNullOrBlank() &&
            !symbol.isNullOrBlank() &&
            (side.equals("buy", ignoreCase = true) || side.equals("sell", ignoreCase = true)) &&
            price?.let { it.isFinite() && it > 0.0 } == true &&
            shares?.let { it.isFinite() && it > 0.0 } == true &&
            transactionTime != null

    private fun MutableMap<String, Int>.increment(reason: String) {
        this[reason] = getOrElse(reason) { 0 } + 1
    }

    private suspend fun findBindingConflict(portfolioId: Long, source: String) =
        portfolioRepository.getAll().firstOrNull { portfolio ->
            if (portfolio.id == portfolioId) return@firstOrNull false
            if (portfolioSettings.getString(portfolio.id, AlpacaSettings.LAST_SYNCED_SOURCE) == source) {
                return@firstOrNull true
            }
            transactionRepository.getByPortfolio(portfolio.id).any { transaction ->
                transaction.externalId?.startsWith("alpaca:$source:") == true
            }
        }

    private fun credentialsFor(portfolio: Portfolio): AlpacaBrokerCredentials? {
        if (portfolio.broker != Broker.ALPACA) return null
        val secrets = credentialsRepository.getPortfolioBrokerCredentials(portfolio.credentialRef)
            as? AlpacaBrokerSecrets
            ?: return null
        return AlpacaBrokerCredentials(
            keyId = secrets.keyId,
            secretKey = secrets.secretKey,
            environment = portfolio.alpacaEnvironment ?: AlpacaEnvironment.PAPER,
        )
    }

    private data class FeeBucket(
        val date: LocalDate,
        val subtype: String,
    )

    companion object {
        const val TASK_ID = "alpaca-import"
        const val TASK_LABEL = "Alpaca import"
        const val FEE_REPLAY_DAYS = 95
        private val SUPPORTED_FEE_TYPES = setOf("REG", "TAF", "CAT")
    }
}
