package io.earlisreal.ejournal.domain.broker

/** Result shared by every broker's transaction synchronization service. */
data class BrokerSyncDetail(
    val skipped: Map<String, Int> = emptyMap(),
)

sealed interface BrokerSyncOutcome {
    data object NotConfigured : BrokerSyncOutcome

    data class Imported(
        val inserted: Int,
        val detail: BrokerSyncDetail = BrokerSyncDetail(),
    ) : BrokerSyncOutcome

    data class AccountAlreadyBound(val portfolioName: String) : BrokerSyncOutcome
    data object InvalidCredentials : BrokerSyncOutcome
    data class NetworkError(val message: String) : BrokerSyncOutcome
}
