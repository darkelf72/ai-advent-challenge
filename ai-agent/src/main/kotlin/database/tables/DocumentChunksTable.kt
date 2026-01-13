package database.tables

import org.jetbrains.exposed.sql.Table

/**
 * Table for storing document chunks with embeddings
 */
object DocumentChunksTable : Table("document_chunks") {
    val id = integer("id").autoIncrement()
    val documentId = integer("document_id").references(DocumentsTable.id)
    val chunkIndex = integer("chunk_index")
    val chunkText = text("chunk_text")
    val embeddingJson = text("embedding_json")  // JSON format for debugging
    val embeddingBlob = blob("embedding_blob")  // Binary format for fast operations
    val tokenCount = integer("token_count")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
