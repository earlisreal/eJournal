package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.database.AppDatabase
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.model.Tag

class SqlDelightTagRepository(private val db: AppDatabase) : TagRepository {

    override suspend fun getAll(portfolioId: Long): List<Tag> =
        db.tagQueries.selectAllTags(portfolioId).executeAsList().map { it.toDomain() }

    override suspend fun create(portfolioId: Long, name: String, color: String): Long =
        // last_insert_rowid() is connection-local; a transaction pins it to the INSERT's connection
        // (JdbcSqliteDriver pools connections for file-backed DBs). Mirrors the transaction repo.
        db.tagQueries.transactionWithResult {
            db.tagQueries.insertTag(portfolioId, name, color)
            db.tagQueries.lastInsertRowId().executeAsOne()
        }

    override suspend fun update(portfolioId: Long, id: Long, name: String, color: String) {
        db.tagQueries.updateTag(name, color, id, portfolioId)
    }

    override suspend fun delete(portfolioId: Long, id: Long) {
        db.tagQueries.transaction {
            if (db.tagQueries.selectTagById(id, portfolioId).executeAsOneOrNull() != null) {
                db.positionTagQueries.deleteAssignmentsForTag(id)
                db.tagQueries.deleteTag(id, portfolioId)
            }
        }
    }

    override suspend fun getTagsForOpeningTxIds(portfolioId: Long, openingTxIds: List<Long>): Map<Long, List<Tag>> {
        if (openingTxIds.isEmpty()) return emptyMap()
        return db.positionTagQueries
            .selectTagsForOpeningTxIds(openingTxIds, portfolioId, portfolioId) { openingTxId, id, tagPortfolioId, name, color ->
                openingTxId to Tag(id, tagPortfolioId, name, color)
            }
            .executeAsList()
            .groupBy({ it.first }, { it.second })
    }

    override suspend fun addTag(portfolioId: Long, openingTxId: Long, tagId: Long) {
        db.positionTagQueries.transaction {
            checkOwners(portfolioId, openingTxId, tagId)
            db.positionTagQueries.insertAssignment(openingTxId, tagId)
        }
    }

    override suspend fun removeTag(portfolioId: Long, openingTxId: Long, tagId: Long) {
        db.positionTagQueries.transaction {
            checkOwners(portfolioId, openingTxId, tagId)
            db.positionTagQueries.deleteAssignment(openingTxId, tagId)
        }
    }

    private fun checkOwners(portfolioId: Long, openingTxId: Long, tagId: Long) {
        val tag = db.tagQueries.selectTagById(tagId, portfolioId).executeAsOneOrNull()
        val transaction = db.tradeTransactionQueries.selectById(openingTxId).executeAsOneOrNull()
        check(tag != null && transaction?.portfolioId == portfolioId) {
            "Tag assignment crosses portfolio ownership"
        }
    }

    private fun io.earlisreal.ejournal.Tag.toDomain() =
        Tag(id, portfolioId, name, color)
}
