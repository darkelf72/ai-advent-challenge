package embedding.rag

/**
 * Interface for RAG (Retrieval-Augmented Generation) client
 * Provides methods for creating embeddings and augmenting prompts with context
 */
interface RagClient {
    /**
     * Get embedding for the given text
     * @param text Input text
     * @return Embedding vector
     */
    suspend fun getEmbedding(text: String): List<Float>

    /**
     * Augment user prompt with relevant context from vector database
     * @param userPrompt Original user prompt
     * @return Augmented prompt with context
     */
    suspend fun augmentPromptWithContext(userPrompt: String): String
}
