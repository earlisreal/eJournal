package io.earlisreal.ejournal.data.repository

interface PositionNoteRepository {
    /** Notes for the given position-opening transaction ids. Positions without notes are absent. */
    suspend fun getNotesForOpeningTxIds(openingTxIds: List<Long>): Map<Long, String>

    /** Stores a note, or removes it when [note] is blank. */
    suspend fun setNote(openingTxId: Long, note: String)
}
