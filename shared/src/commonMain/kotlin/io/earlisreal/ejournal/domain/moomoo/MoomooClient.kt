package io.earlisreal.ejournal.domain.moomoo

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

enum class MoomooAccountEnvironment { REAL, SIMULATE, UNKNOWN }
enum class MoomooAccountRole { NORMAL, MASTER, IPO, UNKNOWN }
enum class MoomooMarket { US, OTHER, UNKNOWN }
enum class MoomooSide { BUY, SELL, SELL_SHORT, BUY_BACK, UNKNOWN }

data class MoomooAccount(
    val id: String,
    val label: String,
    val securityFirm: String,
    val environment: MoomooAccountEnvironment,
    val role: MoomooAccountRole,
    val authorizedMarkets: Set<MoomooMarket>,
    val active: Boolean,
)

/** Accounts supported by the importer: active, live, non-master US securities accounts. */
fun Iterable<MoomooAccount>.eligibleForUsStocks(): List<MoomooAccount> =
    filter {
        it.active &&
            it.environment == MoomooAccountEnvironment.REAL &&
            it.role == MoomooAccountRole.NORMAL &&
            MoomooMarket.US in it.authorizedMarkets
    }

data class MoomooOrder(
    val id: String,
    val symbol: String,
    val side: MoomooSide,
    val createdAt: LocalDateTime?,
    val filledQuantity: Double,
    val market: MoomooMarket,
    val isCombo: Boolean = false,
    val isPrediction: Boolean = false,
)

data class MoomooExecution(
    val orderId: String,
    val symbol: String,
    val side: MoomooSide,
    val quantity: Double,
    val price: Double,
    val executedAt: LocalDateTime?,
    val market: MoomooMarket,
)

data class MoomooOrderFee(
    val orderId: String,
    /** Null means OpenD did not provide the required exact order total. */
    val amount: Double?,
)

sealed interface MoomooResult<out T> {
    data class Success<T>(val value: T) : MoomooResult<T>
    data class Failure(val message: String) : MoomooResult<Nothing>
}

interface MoomooSession {
    suspend fun getAccounts(): MoomooResult<List<MoomooAccount>>
    suspend fun getHistoricalOrders(accountId: String, from: LocalDate, to: LocalDate): MoomooResult<List<MoomooOrder>>
    suspend fun getHistoricalExecutions(accountId: String, from: LocalDate, to: LocalDate): MoomooResult<List<MoomooExecution>>
    suspend fun getOrderFees(accountId: String, orderIds: List<String>): MoomooResult<List<MoomooOrderFee>>
    fun close()
}

/** Platform-neutral seam. The JVM implementation is the only code that imports Moomoo's SDK. */
interface MoomooClient {
    suspend fun open(port: Int): MoomooResult<MoomooSession>
}

sealed interface MoomooConnectionResult {
    data class Connected(val accounts: List<MoomooAccount>) : MoomooConnectionResult
    data class NetworkError(val message: String) : MoomooConnectionResult
}

suspend fun MoomooClient.discoverEligibleAccounts(port: Int): MoomooConnectionResult =
    when (val opened = open(port)) {
        is MoomooResult.Failure -> MoomooConnectionResult.NetworkError(opened.message)
        is MoomooResult.Success -> try {
            when (val accounts = opened.value.getAccounts()) {
                is MoomooResult.Failure -> MoomooConnectionResult.NetworkError(accounts.message)
                is MoomooResult.Success -> MoomooConnectionResult.Connected(accounts.value.eligibleForUsStocks())
            }
        } finally {
            opened.value.close()
        }
    }
