package embedding.model

/**
 * Document chunk with embedding
 */
data class DocumentChunk(
    val id: Int,
    val documentId: Int,
    val chunkIndex: Int,
    val chunkText: String,
    val embedding: List<Float>,
    val tokenCount: Int,
    val createdAt: Long
)
