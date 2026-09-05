package io.earlisreal.ejournal.domain.alpaca

import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.broker.BrokerSyncDetail
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class AlpacaAccount(
    val id: String,
    val accountNumber: String?,
    val status: String?,
)

data class AlpacaBrokerCredentials(
    val keyId: String,
    val secretKey: String,
    val environment: AlpacaEnvironment,
)

enum class AlpacaFillAssetClass {
    US_EQUITY,
    OPTION,
    OTHER,
}

data class AlpacaFillActivity(
    val id: String? = null,
    val symbol: String? = null,
    val side: String? = null,
    val price: Double? = null,
    val shares: Double? = null,
    val transactionTime: Instant? = null,
    val assetClass: AlpacaFillAssetClass = AlpacaFillAssetClass.OTHER,
)

data class AlpacaFeeActivity(
    val id: String? = null,
    val subtype: String? = null,
    val feeDate: LocalDate? = null,
    val createdAt: Instant? = null,
    val status: String? = null,
    val currency: String? = null,
    val netAmount: Double? = null,
)

sealed interface AlpacaConnectionResult {
    data class Connected(
        val account: AlpacaAccount,
        val environment: AlpacaEnvironment,
    ) : AlpacaConnectionResult

    data object InvalidCredentials : AlpacaConnectionResult
    data class NetworkError(val message: String) : AlpacaConnectionResult
}

sealed interface AlpacaFetchResult {
    data class Success(
        val transactions: List<Transaction>,
        val account: AlpacaAccount,
        val detail: BrokerSyncDetail = BrokerSyncDetail(),
        val fills: List<AlpacaFillActivity> = emptyList(),
        val fees: List<AlpacaFeeActivity> = emptyList(),
    ) : AlpacaFetchResult

    data object InvalidCredentials : AlpacaFetchResult
    data class NetworkError(val message: String) : AlpacaFetchResult
}

interface AlpacaBrokerClient {
    suspend fun testConnection(credentials: AlpacaBrokerCredentials): AlpacaConnectionResult

    suspend fun fetchFills(
        credentials: AlpacaBrokerCredentials,
        portfolioId: Long,
        after: Instant?,
        until: Instant?,
    ): AlpacaFetchResult
}
