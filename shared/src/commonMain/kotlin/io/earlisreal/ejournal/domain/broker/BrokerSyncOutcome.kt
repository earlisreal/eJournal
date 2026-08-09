package io.earlisreal.ejournal.domain.broker

/** Result shared by every broker's transaction synchronization service. */
sealed interface BrokerSyncOutcome {
    data class Imported(
        val inserted: Int,
        val skippedOptions: Int = 0,
        val skippedCrypto: Int = 0,
    ) : BrokerSyncOutcome

    data object InvalidCredentials : BrokerSyncOutcome
    data class NetworkError(val message: String) : BrokerSyncOutcome
}
