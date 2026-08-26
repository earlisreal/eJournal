package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.data.repository.PositionNoteRepository
import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Loads and silently autosaves the one user-authored note associated with a position. */
class PositionNoteService(
    private val repository: PositionNoteRepository,
) {

    suspend fun forPosition(position: ClosedPosition): String? {
        val openingTxId = position.openingTransactionId ?: return null
        return repository.getNotesForOpeningTxIds(listOf(openingTxId))[openingTxId]
    }

    suspend fun setNote(position: ClosedPosition, note: String) {
        val openingTxId = position.openingTransactionId ?: return
        try {
            repository.setNote(openingTxId, note)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            delay(RETRY_DELAY_MS)
            try {
                repository.setNote(openingTxId, note)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // ponytail: two silent attempts; add visible retry only if failures prove actionable.
            }
        }
    }

    private companion object {
        const val RETRY_DELAY_MS = 500L
    }
}
