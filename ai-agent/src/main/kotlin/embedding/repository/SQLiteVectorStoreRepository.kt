package embedding.repository

import database.EmbeddingDatabaseManager
import database.tables.DocumentChunksTable
import database.tables.DocumentsTable
import embedding.model.DocumentChunk
import embedding.model.DocumentInfo
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

/**
 * SQLite implementation of VectorStoreRepository
 * Can be easily replaced with dedicated vector database in the future
 */
class SQLiteVectorStoreRepository : VectorStoreRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val db = EmbeddingDatabaseManager.getDatabase()

    override fun findByFileHash(fileHash: String): DocumentInfo? {
        return try {
            transaction(db) {
                DocumentsTable.selectAll()
                    .where { DocumentsTable.fileHash eq fileHash }
                    .map { rowToDocumentInfo(it) }
                    .singleOrNull()
            }
        } catch (e: Exception) {
            logger.error("Failed to find document by hash: $fileHash", e)
            null
        }
    }

    override fun createDocument(
        fileName: String,
        filePath: String,
        fileHash: String,
        fileSizeBytes: Int,
        totalChunks: Int,
        embeddingModel: String
    ): Int {
        return try {
            transaction(db) {
                val now = System.currentTimeMillis()
                DocumentsTable.insert {
                    it[DocumentsTable.fileName] = fileName
                    it[DocumentsTable.filePath] = filePath
                    it[DocumentsTable.fileHash] = fileHash
                    it[DocumentsTable.fileSizeBytes] = fileSizeBytes
                    it[DocumentsTable.totalChunks] = totalChunks
                    it[DocumentsTable.embeddingModel] = embeddingModel
                    it[createdAt] = now
                    it[updatedAt] = now
                }[DocumentsTable.id]
            }.also {
                logger.info("Created document: id=$it, fileName=$fileName, chunks=$totalChunks")
            }
        } catch (e: Exception) {
            logger.error("Failed to create document: $fileName", e)
            throw e
        }
    }

    override fun deleteDocument(documentId: Int) {
        try {
            transaction(db) {
                // First delete all chunks (CASCADE should handle this, but explicit is better)
                DocumentChunksTable.deleteWhere { DocumentChunksTable.documentId eq documentId }
                // Then delete the document
                DocumentsTable.deleteWhere { DocumentsTable.id eq documentId }
            }
            logger.info("Deleted document with id: $documentId")
        } catch (e: Exception) {
            logger.error("Failed to delete document: $documentId", e)
            throw e
        }
    }

    override fun saveChunk(
        documentId: Int,
        chunkIndex: Int,
        chunkText: String,
        embedding: List<Float>,
        tokenCount: Int
    ) {
        try {
            transaction(db) {
                val embeddingJson = Json.encodeToString(embedding)
                val embeddingBytes = floatListToBlob(embedding)

                DocumentChunksTable.insert {
                    it[DocumentChunksTable.documentId] = documentId
                    it[DocumentChunksTable.chunkIndex] = chunkIndex
                    it[DocumentChunksTable.chunkText] = chunkText
                    it[DocumentChunksTable.embeddingJson] = embeddingJson
                    it[DocumentChunksTable.embeddingBlob] = ExposedBlob(embeddingBytes)
                    it[DocumentChunksTable.tokenCount] = tokenCount
                    it[createdAt] = System.currentTimeMillis()
                }
            }
            logger.debug("Saved chunk: documentId=$documentId, chunkIndex=$chunkIndex, tokens=$tokenCount")
        } catch (e: Exception) {
            logger.error("Failed to save chunk: documentId=$documentId, chunkIndex=$chunkIndex", e)
            throw e
        }
    }

    override fun getChunksByDocumentId(documentId: Int): List<DocumentChunk> {
        return try {
            transaction(db) {
                DocumentChunksTable.selectAll()
                    .where { DocumentChunksTable.documentId eq documentId }
                    .orderBy(DocumentChunksTable.chunkIndex to SortOrder.ASC)
                    .map { rowToDocumentChunk(it) }
            }
        } catch (e: Exception) {
            logger.error("Failed to get chunks for document: $documentId", e)
            emptyList()
        }
    }

    override fun getAllDocuments(): List<DocumentInfo> {
        return try {
            transaction(db) {
                DocumentsTable.selectAll()
                    .orderBy(DocumentsTable.createdAt to SortOrder.DESC)
                    .map { rowToDocumentInfo(it) }
            }
        } catch (e: Exception) {
            logger.error("Failed to get all documents", e)
            emptyList()
        }
    }

    // Helper methods

    private fun rowToDocumentInfo(row: ResultRow): DocumentInfo {
        return DocumentInfo(
            id = row[DocumentsTable.id],
            fileName = row[DocumentsTable.fileName],
            filePath = row[DocumentsTable.filePath],
            fileHash = row[DocumentsTable.fileHash],
            fileSizeBytes = row[DocumentsTable.fileSizeBytes],
            totalChunks = row[DocumentsTable.totalChunks],
            embeddingModel = row[DocumentsTable.embeddingModel],
            createdAt = row[DocumentsTable.createdAt],
            updatedAt = row[DocumentsTable.updatedAt]
        )
    }

    private fun rowToDocumentChunk(row: ResultRow): DocumentChunk {
        val embeddingBlob = row[DocumentChunksTable.embeddingBlob]
        val embedding = blobToFloatList(embeddingBlob.bytes)

        return DocumentChunk(
            id = row[DocumentChunksTable.id],
            documentId = row[DocumentChunksTable.documentId],
            chunkIndex = row[DocumentChunksTable.chunkIndex],
            chunkText = row[DocumentChunksTable.chunkText],
            embedding = embedding,
            tokenCount = row[DocumentChunksTable.tokenCount],
            createdAt = row[DocumentChunksTable.createdAt]
        )
    }

    /**
     * Convert List<Float> to binary blob for efficient storage
     */
    private fun floatListToBlob(floats: List<Float>): ByteArray {
        val byteBuffer = ByteBuffer.allocate(floats.size * 4)
        floats.forEach { byteBuffer.putFloat(it) }
        return byteBuffer.array()
    }

    /**
     * Convert binary blob back to List<Float>
     */
    private fun blobToFloatList(bytes: ByteArray): List<Float> {
        val byteBuffer = ByteBuffer.wrap(bytes)
        val floats = mutableListOf<Float>()
        while (byteBuffer.hasRemaining()) {
            floats.add(byteBuffer.float)
        }
        return floats
    }
}
