package embedding.model

/**
 * Document chunk with similarity score
 * Used for ranking search results
 */
data class ScoredChunk(
    val chunk: DocumentChunk,
    val score: Float
)
