package embedding.chunking

/**
 * Factory for creating appropriate chunking strategies based on file type.
 */
object ChunkingStrategyFactory {
    // nomic-embed-text has context length of 8192 tokens, so we can use larger chunks
    private const val MAX_TOKENS_PER_CHUNK = 500
    private const val OVERLAP_TOKENS = 100

    /**
     * Get chunking strategy for a given file extension.
     *
     * @param fileExtension File extension (without dot, e.g., "txt", "md")
     * @return ChunkingStrategy instance for this file type
     * @throws UnsupportedFileTypeException if file type is not supported
     */
    fun getStrategy(fileExtension: String): ChunkingStrategy {
        return when (fileExtension.lowercase()) {
            "md", "markdown" -> MarkdownChunkingStrategy(
                maxTokens = MAX_TOKENS_PER_CHUNK,
                overlapTokens = OVERLAP_TOKENS
            )
            "txt" -> PlainTextChunkingStrategy(
                maxTokens = MAX_TOKENS_PER_CHUNK,
                overlapTokens = OVERLAP_TOKENS
            )
            else -> throw UnsupportedFileTypeException(
                "File type .$fileExtension is not supported. Supported types: ${getSupportedExtensions().joinToString { ".$it" }}"
            )
        }
    }

    /**
     * Get set of all supported file extensions (without dots).
     *
     * @return Set of supported extensions ("txt", "md", "markdown")
     */
    fun getSupportedExtensions(): Set<String> {
        return setOf("txt", "md", "markdown")
    }
}
