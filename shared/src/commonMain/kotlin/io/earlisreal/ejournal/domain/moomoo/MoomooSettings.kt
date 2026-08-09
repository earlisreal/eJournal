package io.earlisreal.ejournal.domain.moomoo

import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository

data class MoomooPortfolioConfig(
    val port: Int = MoomooSettings.DEFAULT_PORT,
    val accountId: String,
    val accountLabel: String,
    val securityFirm: String,
) {
    val source: String get() = MoomooSettings.source(accountId)
}

object MoomooSettings {
    const val PORT = "moomoo.port"
    const val ACCOUNT_ID = "moomoo.accountId"
    const val ACCOUNT_LABEL = "moomoo.accountLabel"
    const val SECURITY_FIRM = "moomoo.securityFirm"
    const val ACCOUNT_SOURCE = "moomoo.accountSource"
    const val LAST_COMPLETED_DATE = "moomoo.lastCompletedDate"
    const val LAST_SYNCED_SOURCE = "moomoo.lastSyncedSource"
    const val AUTO_SYNC_ON_STARTUP = "moomoo.autoSyncOnStartup"
    const val AUTO_SYNC_DEFAULT = false
    const val DEFAULT_PORT = 11111

    fun source(accountId: String): String = "moomoo:REAL:$accountId"
}

suspend fun PortfolioSettingsRepository.getMoomooConfig(portfolioId: Long): MoomooPortfolioConfig? {
    val accountId = getString(portfolioId, MoomooSettings.ACCOUNT_ID)?.takeIf { it.isNotBlank() } ?: return null
    val label = getString(portfolioId, MoomooSettings.ACCOUNT_LABEL) ?: return null
    val firm = getString(portfolioId, MoomooSettings.SECURITY_FIRM) ?: return null
    val port = getString(portfolioId, MoomooSettings.PORT)?.toIntOrNull() ?: return null
    if (port !in 1..65535) return null
    return MoomooPortfolioConfig(port, accountId, label, firm)
}

suspend fun PortfolioSettingsRepository.putMoomooConfig(portfolioId: Long, config: MoomooPortfolioConfig) {
    require(config.port in 1..65535) { "OpenD port must be between 1 and 65535" }
    require(config.accountId.isNotBlank()) { "Select a Moomoo account" }
    putString(portfolioId, MoomooSettings.PORT, config.port.toString())
    putString(portfolioId, MoomooSettings.ACCOUNT_ID, config.accountId)
    putString(portfolioId, MoomooSettings.ACCOUNT_LABEL, config.accountLabel)
    putString(portfolioId, MoomooSettings.SECURITY_FIRM, config.securityFirm)
    putString(portfolioId, MoomooSettings.ACCOUNT_SOURCE, config.source)
}
