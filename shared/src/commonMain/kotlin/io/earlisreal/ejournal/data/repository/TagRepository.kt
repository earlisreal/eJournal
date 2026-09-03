package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.model.Tag

interface TagRepository {
    /** All tags for [portfolioId], ordered by name (case-insensitive). */
    suspend fun getAll(portfolioId: Long): List<Tag>

    /** Creates a tag, returning its new id. Throws if [name] duplicates within the portfolio. */
    suspend fun create(portfolioId: Long, name: String, color: String): Long

    suspend fun update(portfolioId: Long, id: Long, name: String, color: String)

    /** Deletes a tag and all of its position assignments. */
    suspend fun delete(portfolioId: Long, id: Long)

    /** Tags for each opening transaction in [portfolioId]. Untagged positions are absent from the map. */
    suspend fun getTagsForOpeningTxIds(portfolioId: Long, openingTxIds: List<Long>): Map<Long, List<Tag>>

    /** Assigns [tagId] to the position opened by [openingTxId]. Idempotent within [portfolioId]. */
    suspend fun addTag(portfolioId: Long, openingTxId: Long, tagId: Long)

    suspend fun removeTag(portfolioId: Long, openingTxId: Long, tagId: Long)
}
