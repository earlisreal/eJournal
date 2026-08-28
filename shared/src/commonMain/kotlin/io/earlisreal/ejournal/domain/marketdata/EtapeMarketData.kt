package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.domain.model.ClosedPosition

data class EtapeImportResult(
    val importedBars: Int = 0,
    val skippedBars: Int = 0,
    val warning: String? = null,
)

interface EtapeMarketDataImporter {
    suspend fun importFor(positions: List<ClosedPosition>): EtapeImportResult
}
