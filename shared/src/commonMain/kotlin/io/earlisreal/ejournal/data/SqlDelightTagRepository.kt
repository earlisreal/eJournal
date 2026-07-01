package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.model.Tag

class SqlDelightTagRepository(private val db: AppDatabase) : TagRepository {

    override suspend fun getAll(): List<Tag> =
        db.tagQueries.selectAllTags().executeAsList().map { Tag(it.id, it.name, it.color) }

    override suspend fun create(name: String, color: String): Long =
        // last_insert_rowid() is connection-local; a transaction pins it to the INSERT's connection
        // (JdbcSqliteDriver pools connections for file-backed DBs). Mirrors the transaction repo.
        db.tagQueries.transactionWithResult {
            db.tagQueries.insertTag(name, color)
            db.tagQueries.lastInsertRowId().executeAsOne()
        }

    override suspend fun update(id: Long, name: String, color: String) {
        db.tagQueries.updateTag(name, color, id)
    }

    override suspend fun delete(id: Long) {
        db.tagQueries.transaction {
            db.positionTagQueries.deleteAssignmentsForTag(id)
            db.tagQueries.deleteTag(id)
        }
    }

    override suspend fun getTagsForOpeningTxIds(openingTxIds: List<Long>): Map<Long, List<Tag>> {
        if (openingTxIds.isEmpty()) return emptyMap()
        return db.positionTagQueries
            .selectTagsForOpeningTxIds(openingTxIds) { openingTxId, id, name, color ->
                openingTxId to Tag(id, name, color)
            }
            .executeAsList()
            .groupBy({ it.first }, { it.second })
    }

    override suspend fun addTag(openingTxId: Long, tagId: Long) {
        db.positionTagQueries.insertAssignment(openingTxId, tagId)
    }

    override suspend fun removeTag(openingTxId: Long, tagId: Long) {
        db.positionTagQueries.deleteAssignment(openingTxId, tagId)
    }
}
