package embedding.model

/**
 * Text chunk model for document processing
 */
data class TextChunk(
    val text: String,
    val estimatedTokenCount: Int
)
