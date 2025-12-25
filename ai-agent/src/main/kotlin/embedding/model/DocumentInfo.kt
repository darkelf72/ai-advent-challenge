package embedding.model

/**
 * Document metadata model
 */
data class DocumentInfo(
    val id: Int,
    val fileName: String,
    val filePath: String,
    val fileHash: String,
    val fileSizeBytes: Int,
    val totalChunks: Int,
    val embeddingModel: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)
