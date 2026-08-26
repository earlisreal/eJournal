package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.data.repository.PositionNoteRepository
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.TradeDirection
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PositionNoteServiceTest {

    @Test
    fun loadsAndSavesNoteForPositionOpeningTransaction() = runTest {
        val repo = FakePositionNoteRepository().apply { notes[42L] = "Followed the plan" }
        val service = PositionNoteService(repo)
        val position = positionOpenedBy(42L)

        assertEquals("Followed the plan", service.forPosition(position))

        service.setNote(position, "Exited at resistance")

        assertEquals("Exited at resistance", repo.notes[42L])
    }

    @Test
    fun retriesOnceAfterSaveFailure() = runTest {
        val repo = FakePositionNoteRepository(failuresBeforeSuccess = 1)
        val service = PositionNoteService(repo)

        service.setNote(positionOpenedBy(42L), "Retry me")

        assertEquals(2, repo.writeAttempts)
        assertEquals("Retry me", repo.notes[42L])
    }

    @Test
    fun swallowsSecondSaveFailure() = runTest {
        val repo = FakePositionNoteRepository(failuresBeforeSuccess = 2)
        val service = PositionNoteService(repo)

        service.setNote(positionOpenedBy(42L), "May be lost")

        assertEquals(2, repo.writeAttempts)
        assertEquals(emptyMap(), repo.notes)
    }

    private fun positionOpenedBy(id: Long) = ClosedPosition(
        symbol = "AAPL",
        entryDatetime = LocalDateTime(2026, 6, 1, 9, 30),
        exitDatetime = LocalDateTime(2026, 6, 1, 10, 0),
        averageEntryPrice = 100.0,
        averageExitPrice = 105.0,
        shares = 10.0,
        fees = 1.0,
        profitLoss = 49.0,
        transactions = listOf(
            Transaction(
                id = id,
                portfolioId = 1L,
                symbol = "AAPL",
                datetime = LocalDateTime(2026, 6, 1, 9, 30),
                action = Action.BUY,
                price = 100.0,
                shares = 10.0,
                fees = 1.0,
            ),
        ),
        direction = TradeDirection.LONG,
    )

    private class FakePositionNoteRepository(
        private var failuresBeforeSuccess: Int = 0,
    ) : PositionNoteRepository {
        val notes = mutableMapOf<Long, String>()
        var writeAttempts = 0

        override suspend fun getNotesForOpeningTxIds(openingTxIds: List<Long>): Map<Long, String> =
            notes.filterKeys { it in openingTxIds }

        override suspend fun setNote(openingTxId: Long, note: String) {
            writeAttempts++
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--
                error("write failed")
            }
            if (note.isBlank()) notes.remove(openingTxId) else notes[openingTxId] = note
        }
    }
}
