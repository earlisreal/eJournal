package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.model.Tag

interface TagRepository {
    /** All tags, ordered by name (case-insensitive). */
    suspend fun getAll(): List<Tag>

    /** Creates a tag, returning its new id. Throws if [name] duplicates an existing name (case-insensitive). */
    suspend fun create(name: String, color: String): Long

    suspend fun update(id: Long, name: String, color: String)

    /** Deletes a tag and all of its position assignments. */
    suspend fun delete(id: Long)

    /** Tags for each of the given opening transaction ids. Positions with no tags are absent from the map. */
    suspend fun getTagsForOpeningTxIds(openingTxIds: List<Long>): Map<Long, List<Tag>>

    /** Assigns [tagId] to the position opened by [openingTxId]. Idempotent. */
    suspend fun addTag(openingTxId: Long, tagId: Long)

    suspend fun removeTag(openingTxId: Long, tagId: Long)
}
