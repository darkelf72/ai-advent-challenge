package database.tables

import org.jetbrains.exposed.sql.Table

/**
 * Table for storing document metadata
 */
object DocumentsTable : Table("documents") {
    val id = integer("id").autoIncrement()
    val fileName = varchar("file_name", 255)
    val filePath = text("file_path")
    val fileHash = varchar("file_hash", 64).uniqueIndex()
    val fileSizeBytes = integer("file_size_bytes")
    val totalChunks = integer("total_chunks")
    val embeddingModel = varchar("embedding_model", 100)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
