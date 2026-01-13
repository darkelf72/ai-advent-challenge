package embedding.chunking

/**
 * Strategy interface for chunking documents into smaller pieces with embeddings.
 * Different implementations handle different document formats (plain text, Markdown, etc.)
 */
interface ChunkingStrategy {
    /**
     * Split document content into semantic chunks.
     *
     * @param content Raw document content
     * @param metadata Document metadata (filename, type, etc.)
     * @return List of text chunks with metadata
     */
    fun splitIntoChunks(
        content: String,
        metadata: DocumentMetadata
    ): List<ChunkWithMetadata>
}

/**
 * Metadata about the document being chunked.
 *
 * @property fileName Name of the file
 * @property fileExtension File extension (e.g., "txt", "md")
 * @property originalName Original filename if different from fileName
 */
data class DocumentMetadata(
    val fileName: String,
    val fileExtension: String,
    val originalName: String? = null
)

/**
 * A chunk of text with its metadata.
 *
 * @property text The actual text content of the chunk
 * @property estimatedTokenCount Estimated number of tokens in this chunk
 * @property metadata Additional metadata about the chunk (headings, position, etc.)
 */
data class ChunkWithMetadata(
    val text: String,
    val estimatedTokenCount: Int,
    val metadata: ChunkMetadata = ChunkMetadata()
)

/**
 * Metadata about a specific chunk.
 *
 * @property headingPath Hierarchical path of headings (e.g., ["Chapter 1", "Section 1.1"])
 * @property level Heading level (0 for non-heading text, 1-6 for Markdown headings)
 * @property startLine Starting line number in the original document (optional)
 * @property endLine Ending line number in the original document (optional)
 */
data class ChunkMetadata(
    val headingPath: List<String> = emptyList(),
    val level: Int = 0,
    val startLine: Int? = null,
    val endLine: Int? = null
)
