package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.model.ClosedPosition

/**
 * Reads closed positions with their tags attached, and mediates per-position tag writes.
 *
 * Tag hydration is deliberately layered on top of [ClosedPositionService] rather than baked into its
 * cache: the compute cache is keyed on the transaction signature, so a tag-only change wouldn't
 * invalidate it and cached positions would serve stale tags. Here we take the (cached) positions and
 * re-attach tags on every read via a single [TagRepository.getTagsForOpeningTxIds] lookup, so a tag
 * edit followed by a reload always reflects the latest assignments.
 */
class PositionTagService(
    private val closedPositions: ClosedPositionService,
    private val tagRepository: TagRepository,
) {
    /** The portfolio's closed positions with their tags hydrated. */
    suspend fun forPortfolio(portfolioId: Long): List<ClosedPosition> {
        val positions = closedPositions.forPortfolio(portfolioId)
        val openingTxIds = positions.mapNotNull { it.openingTransactionId }
        if (openingTxIds.isEmpty()) return positions
        val tagsByOpener = tagRepository.getTagsForOpeningTxIds(openingTxIds)
        return positions.map { p ->
            val tags = p.openingTransactionId?.let { tagsByOpener[it] }
            if (tags.isNullOrEmpty()) p else p.copy(tags = tags)
        }
    }

    /** Assigns [tagId] to [position]. No-op if the position carries no transactions (tests/previews). */
    suspend fun addTag(position: ClosedPosition, tagId: Long) {
        position.openingTransactionId?.let { tagRepository.addTag(it, tagId) }
    }

    suspend fun removeTag(position: ClosedPosition, tagId: Long) {
        position.openingTransactionId?.let { tagRepository.removeTag(it, tagId) }
    }
}
