package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.repository.PositionNoteRepository

class SqlDelightPositionNoteRepository(private val db: AppDatabase) : PositionNoteRepository {

    override suspend fun getNotesForOpeningTxIds(openingTxIds: List<Long>): Map<Long, String> {
        if (openingTxIds.isEmpty()) return emptyMap()
        return db.positionNoteQueries
            .selectNotesForOpeningTxIds(openingTxIds) { openingTxId, note -> openingTxId to note }
            .executeAsList()
            .toMap()
    }

    override suspend fun setNote(openingTxId: Long, note: String) {
        if (note.isBlank()) {
            db.positionNoteQueries.deleteNote(openingTxId)
        } else {
            db.positionNoteQueries.upsertNote(openingTxId, note)
        }
    }
}
