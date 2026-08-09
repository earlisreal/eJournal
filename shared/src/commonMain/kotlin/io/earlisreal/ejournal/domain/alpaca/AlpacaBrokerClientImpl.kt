package io.earlisreal.ejournal.domain.alpaca

import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.domain.broker.BrokerSyncDetail
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private const val PAGE_SIZE = 100
private const val MAX_ATTEMPTS = 3
private val EASTERN = TimeZone.of("America/New_York")

class AlpacaBrokerClientImpl(
    private val httpClient: HttpClient,
    private val credentialsRepository: CredentialsRepository,
) : AlpacaBrokerClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun testConnection(): AlpacaConnectionResult {
        val credentials = credentialsRepository.getAlpacaCredentials()
            ?: return AlpacaConnectionResult.InvalidCredentials
        return when (val result = requestWithRetry(credentials, "/v2/account")) {
            is RequestResult.Failure -> AlpacaConnectionResult.NetworkError(result.message)
            is RequestResult.Response -> when {
                result.status == HttpStatusCode.Unauthorized || result.status == HttpStatusCode.Forbidden ->
                    AlpacaConnectionResult.InvalidCredentials
                result.status.value >= 400 ->
                    AlpacaConnectionResult.NetworkError(result.errorMessage())
                else -> parseAccount(result.body, credentials.environment)
            }
        }
    }

    override suspend fun fetchFills(
        portfolioId: Long,
        after: Instant?,
        until: Instant?,
    ): AlpacaFetchResult {
        val credentials = credentialsRepository.getAlpacaCredentials()
            ?: return AlpacaFetchResult.InvalidCredentials

        val account = when (val result = requestWithRetry(credentials, "/v2/account")) {
            is RequestResult.Failure -> return AlpacaFetchResult.NetworkError(result.message)
            is RequestResult.Response -> when {
                result.status == HttpStatusCode.Unauthorized || result.status == HttpStatusCode.Forbidden ->
                    return AlpacaFetchResult.InvalidCredentials
                result.status.value >= 400 ->
                    return AlpacaFetchResult.NetworkError(result.errorMessage())
                else -> when (val parsed = parseAccount(result.body, credentials.environment)) {
                    is AlpacaConnectionResult.Connected -> parsed.account
                    AlpacaConnectionResult.InvalidCredentials -> return AlpacaFetchResult.InvalidCredentials
                    is AlpacaConnectionResult.NetworkError -> return AlpacaFetchResult.NetworkError(parsed.message)
                }
            }
        }

        val cryptoSymbols = when (val result = requestWithRetry(credentials, "/v2/assets") {
            parameter("asset_class", "crypto")
        }) {
            is RequestResult.Failure -> return AlpacaFetchResult.NetworkError(result.message)
            is RequestResult.Response -> when {
                result.status == HttpStatusCode.Unauthorized || result.status == HttpStatusCode.Forbidden ->
                    return AlpacaFetchResult.InvalidCredentials
                result.status.value >= 400 ->
                    return AlpacaFetchResult.NetworkError(result.errorMessage())
                else -> parseCryptoSymbols(result.body).getOrElse {
                    return AlpacaFetchResult.NetworkError("Invalid Alpaca asset response")
                }
            }
        }

        val transactions = mutableListOf<Transaction>()
        var skippedOptions = 0
        var skippedCrypto = 0
        var pageToken: String? = null

        try {
            do {
                val page = when (val result = requestWithRetry(credentials, "/v2/account/activities/FILL") {
                    parameter("direction", "asc")
                    parameter("page_size", PAGE_SIZE)
                    after?.let { parameter("after", it.toString()) }
                    until?.let { parameter("until", it.toString()) }
                    pageToken?.let { parameter("page_token", it) }
                }) {
                    is RequestResult.Failure -> return AlpacaFetchResult.NetworkError(result.message)
                    is RequestResult.Response -> when {
                        result.status == HttpStatusCode.Unauthorized || result.status == HttpStatusCode.Forbidden ->
                            return AlpacaFetchResult.InvalidCredentials
                        result.status.value >= 400 ->
                            return AlpacaFetchResult.NetworkError(result.errorMessage())
                        else -> parseActivities(result.body).getOrElse {
                            return AlpacaFetchResult.NetworkError("Invalid Alpaca activity response")
                        }
                    }
                }

                for (fill in page.activities) {
                    when (val mapped = fill.toTransactionOrSkip(portfolioId, credentials.environment, account.id, cryptoSymbols)) {
                        is FillMapping.Accepted -> transactions += mapped.transaction
                        FillMapping.SkipCrypto -> skippedCrypto++
                        FillMapping.SkipOption -> skippedOptions++
                    }
                }

                // The legacy endpoint returns a bare array. Its next cursor is the final activity
                // id, so a full page means there may be another page. Envelope responses are also
                // accepted to keep the parser tolerant of broker-side API changes.
                pageToken = page.nextPageToken
            } while (pageToken != null)
        } catch (e: Exception) {
            return AlpacaFetchResult.NetworkError(e.message ?: "Invalid Alpaca activity response")
        }

        return AlpacaFetchResult.Success(
            transactions = transactions,
            account = account,
            detail = BrokerSyncDetail(
                skipped = mapOf(
                    "options" to skippedOptions,
                    "crypto fills" to skippedCrypto,
                ).filterValues { it > 0 },
            ),
        )
    }

    private suspend fun requestWithRetry(
        credentials: AlpacaCredentials,
        path: String,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): RequestResult {
        var backoffMs = 1_000L
        repeat(MAX_ATTEMPTS) { attempt ->
            val response = try {
                val httpResponse = httpClient.get(credentials.environment.tradingBaseUrl + path) {
                    header("APCA-API-KEY-ID", credentials.keyId)
                    header("APCA-API-SECRET-KEY", credentials.secretKey)
                    configure()
                }
                RequestResult.Response(
                    status = httpResponse.status,
                    body = httpResponse.bodyAsText(),
                    requestId = httpResponse.headers["X-Request-ID"],
                )
            } catch (e: Exception) {
                return RequestResult.Failure(e.message ?: "Request failed")
            }

            if (response.status.value >= 400) {
                response.requestId?.let { println("[Alpaca] ${response.status} requestId=$it") }
            }
            val retryable = response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500
            if (!retryable || attempt == MAX_ATTEMPTS - 1) return response
            response.requestId?.let { println("[Alpaca] retrying ${response.status} requestId=$it") }
            delay(backoffMs.milliseconds)
            backoffMs *= 2
        }
        return RequestResult.Failure("Request failed")
    }

    private fun parseAccount(body: String, environment: AlpacaEnvironment): AlpacaConnectionResult =
        runCatching { json.decodeFromString<AccountResponse>(body) }
            .fold(
                onSuccess = {
                    AlpacaConnectionResult.Connected(
                        account = AlpacaAccount(it.id, it.accountNumber, it.status),
                        environment = environment,
                    )
                },
                onFailure = { AlpacaConnectionResult.NetworkError("Invalid Alpaca account response") },
            )

    private fun parseActivities(body: String): Result<ActivitiesPage> = runCatching {
        val root = json.parseToJsonElement(body)
        when (root) {
            is JsonArray -> {
                val activities = json.decodeFromJsonElement<List<FillRow>>(root)
                ActivitiesPage(
                    activities = activities,
                    nextPageToken = activities.lastOrNull()?.id?.takeIf { activities.size == PAGE_SIZE },
                )
            }
            is JsonObject -> {
                val envelope = json.decodeFromJsonElement<ActivitiesEnvelope>(root)
                ActivitiesPage(
                    activities = envelope.activities,
                    nextPageToken = envelope.nextPageToken
                        ?: envelope.activities.lastOrNull()?.id?.takeIf { envelope.activities.size == PAGE_SIZE },
                )
            }
            else -> error("activity response is not an array or object")
        }
    }

    private fun parseCryptoSymbols(body: String): Result<Set<String>> = runCatching {
        val root = json.parseToJsonElement(body) as? JsonArray
            ?: error("asset response is not an array")
        root.mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            val symbol = (asset["symbol"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val assetClass =
                (asset["class"] as? JsonPrimitive)?.content
                    ?: (asset["asset_class"] as? JsonPrimitive)?.content
            if (assetClass == null || assetClass.startsWith("crypto", ignoreCase = true)) {
                symbol.trim().uppercase()
            } else {
                null
            }
        }.toSet()
    }

    private fun FillRow.toTransactionOrSkip(
        portfolioId: Long,
        environment: AlpacaEnvironment,
        accountId: String,
        cryptoSymbols: Set<String>,
    ): FillMapping {
        val normalizedSymbol = symbol.trim()
        if (normalizedSymbol.contains('/') || normalizedSymbol.uppercase() in cryptoSymbols) {
            return FillMapping.SkipCrypto
        }
        if (isOccOptionSymbol(normalizedSymbol)) return FillMapping.SkipOption

        val action = when (side.lowercase()) {
            "buy" -> Action.BUY
            "sell" -> Action.SELL
            else -> error("unsupported Alpaca fill side")
        }
        val price = price.asDouble() ?: error("invalid Alpaca fill price")
        val shares = qty.asDouble() ?: error("invalid Alpaca fill quantity")
        return FillMapping.Accepted(
            Transaction(
                id = 0L,
                portfolioId = portfolioId,
                symbol = normalizedSymbol,
                datetime = Instant.parse(transactionTime).toLocalDateTime(EASTERN),
                action = action,
                price = price,
                shares = shares,
                fees = 0.0,
                externalId = "alpaca:${environment.name.lowercase()}:$accountId:$id",
            )
        )
    }

    private fun JsonElement.asDouble(): Double? =
        (this as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun isOccOptionSymbol(symbol: String): Boolean =
        OCC_OPTION_REGEX.matches(symbol.uppercase())

    private sealed interface FillMapping {
        data class Accepted(val transaction: Transaction) : FillMapping
        data object SkipOption : FillMapping
        data object SkipCrypto : FillMapping
    }

    private data class ActivitiesPage(
        val activities: List<FillRow>,
        val nextPageToken: String?,
    )

    @Serializable
    private data class AccountResponse(
        val id: String,
        @SerialName("account_number") val accountNumber: String? = null,
        val status: String? = null,
    )

    @Serializable
    private data class ActivitiesEnvelope(
        val activities: List<FillRow> = emptyList(),
        @SerialName("next_page_token") val nextPageToken: String? = null,
    )

    @Serializable
    private data class FillRow(
        val id: String,
        val symbol: String,
        val side: String,
        val price: JsonElement,
        val qty: JsonElement,
        @SerialName("transaction_time") val transactionTime: String,
    )

    private sealed interface RequestResult {
        data class Response(
            val status: HttpStatusCode,
            val body: String,
            val requestId: String?,
        ) : RequestResult {
            fun errorMessage(): String =
                "Alpaca returned HTTP ${status.value}" + (requestId?.let { " (request $it)" } ?: "")
        }

        data class Failure(val message: String) : RequestResult
    }

    companion object {
        // OCC: root (1–6 chars), YYMMDD, call/put, and eight-digit strike. Spaces in the root are
        // optional because Alpaca has returned both padded and unpadded representations.
        private val OCC_OPTION_REGEX = Regex("^[A-Z0-9.]{1,6}\\s*\\d{6}[CP]\\d{8}$")
    }
}
