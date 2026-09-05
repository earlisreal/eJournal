package io.earlisreal.ejournal.domain.broker

/** Result shared by every broker's transaction synchronization service. */
data class BrokerFeeSummary(
    val allocatedFees: Double = 0.0,
    val unappliedFees: Double = 0.0,
    val warnings: Map<String, Int> = emptyMap(),
)

data class BrokerSyncDetail(
    val skipped: Map<String, Int> = emptyMap(),
    val feeSummary: BrokerFeeSummary? = null,
)

fun BrokerSyncDetail.describeImport(inserted: Int): String = buildString {
    append("Imported $inserted new transaction(s)")
    skipped.forEach { (reason, count) ->
        if (count > 0) append(" · $count $reason skipped")
    }
    feeSummary?.let { fee ->
        append(" · Fees: ${"%.4f".format(fee.allocatedFees)} allocated")
        if (fee.unappliedFees != 0.0) append(" · ${"%.4f".format(fee.unappliedFees)} unapplied")
        fee.warnings.forEach { (reason, count) -> append(" · $count $reason") }
    }
}

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
