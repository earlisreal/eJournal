package io.earlisreal.ejournal.domain.alpaca

object AlpacaSettings {
    const val LAST_SYNCED_AT = "alpaca.lastSyncedAt"
    const val LAST_SYNCED_SOURCE = "alpaca.lastSyncedSource"
    const val AUTO_SYNC_ON_STARTUP = "alpaca.autoSyncOnStartup"
    const val AUTO_SYNC_DEFAULT = false

    fun source(environment: AlpacaEnvironment, accountId: String): String =
        "${environment.name.lowercase()}:$accountId"
}
