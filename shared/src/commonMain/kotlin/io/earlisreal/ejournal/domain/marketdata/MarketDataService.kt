package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.FifoMatcher
import io.earlisreal.ejournal.domain.model.Market
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Duration.Companion.milliseconds

data class SyncResult(
    val fetchedSymbols: Int,
    val failedSymbols: List<String>,
    val keysRejected: Boolean,
    /** Some 1-min history was outside Yahoo's window and no Alpaca keys are configured. */
    val needsKeys: Boolean,
)

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data class Syncing(val completed: Int, val total: Int) : SyncStatus()
    data class Finished(val result: SyncResult) : SyncStatus()
}

/**
 * Derive-and-reconcile sync: what's needed is recomputed from transactions (like closed
 * positions, never persisted), what's stored is the coverage check — so any pass heals
 * all gaps and there is no retry bookkeeping. Triggers (post-import, startup, manual
 * retry/sync) all land on [requestSync]/[sync].
 */
class MarketDataService(
    private val portfolioRepository: PortfolioRepository,
    private val transactionRepository: TransactionRepository,
    private val marketDataRepository: MarketDataRepository,
    private val yahooProvider: MarketDataProvider,
    private val alpacaProvider: MarketDataProvider,
    private val credentialsRepository: CredentialsRepository,
    private val scope: CoroutineScope? = null,
    private val todayProvider: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Fire-and-forget for UI triggers; no-op while a sync is already running. */
    fun requestSync() {
        val scope = scope ?: return
        if (_status.value is SyncStatus.Syncing) return
        scope.launch { sync() }
    }

    suspend fun sync(): SyncResult {
        val today = todayProvider()
        val hasKeys = credentialsRepository.getAlpacaCredentials() != null

        val positions = portfolioRepository.getAll()
            .filter { it.market == Market.US_STOCKS }
            .flatMap { FifoMatcher.computeClosedPositions(transactionRepository.getByPortfolio(it.id)) }

        val work = requiredRanges(positions, today)
            .flatMap { range -> subtractCoverage(range, marketDataRepository.getCoverage(range.symbol, range.timeframe)) }
            .flatMap { range -> route(range, today, hasKeys) }
            .groupBy { it.range.symbol }

        _status.value = SyncStatus.Syncing(0, work.size)

        val semaphore = Semaphore(MAX_CONCURRENT_FETCHES)
        val progressMutex = Mutex()
        var completed = 0

        val symbolResults: List<SymbolFetchResult> = coroutineScope {
            work.entries.map { (symbol, routedRanges) ->
                async {
                    val result = semaphore.withPermit { fetchSymbol(symbol, routedRanges) }
                    progressMutex.withLock {
                        completed++
                        _status.value = SyncStatus.Syncing(completed, work.size)
                    }
                    result
                }
            }.awaitAll()
        }

        val result = SyncResult(
            fetchedSymbols = symbolResults.count { it.fetched },
            failedSymbols = symbolResults.filter { it.failed }.map { it.symbol },
            keysRejected = symbolResults.any { it.keysRejected },
            needsKeys = symbolResults.any { it.needsKeys },
        )
        _status.value = SyncStatus.Finished(result)
        return result
    }

    private suspend fun fetchSymbol(symbol: String, routedRanges: List<RoutedRange>): SymbolFetchResult {
        var fetched = false
        var failed = false
        var keysRejected = false
        var needsKeys = false

        symbolLoop@ for (routed in routedRanges) {
            val provider = when (routed.source) {
                BarSource.YAHOO -> yahooProvider
                BarSource.ALPACA -> {
                    if (keysRejected) continue@symbolLoop
                    alpacaProvider
                }
                BarSource.UNAVAILABLE -> {
                    needsKeys = true
                    continue@symbolLoop
                }
            }
            try {
                val bars = fetchWithRetry(provider, routed.range)
                marketDataRepository.upsertBars(bars)
                fetched = true
            } catch (e: InvalidKeysException) {
                keysRejected = true
            } catch (e: SymbolNotFoundException) {
                break@symbolLoop
            } catch (e: TransientFetchException) {
                failed = true
                break@symbolLoop
            }
        }

        return SymbolFetchResult(symbol, fetched, failed, keysRejected, needsKeys)
    }

    private suspend fun fetchWithRetry(provider: MarketDataProvider, range: BarRange): List<Bar> =
        try {
            provider.getBars(range.symbol, range.timeframe, range.from, range.to)
        } catch (e: TransientFetchException) {
            delay(RETRY_DELAY_MS.milliseconds)
            provider.getBars(range.symbol, range.timeframe, range.from, range.to)
        }

    private data class SymbolFetchResult(
        val symbol: String,
        val fetched: Boolean,
        val failed: Boolean,
        val keysRejected: Boolean,
        val needsKeys: Boolean,
    )

    companion object {
        private const val RETRY_DELAY_MS = 2_000L
        private const val MAX_CONCURRENT_FETCHES = 6
    }
}
