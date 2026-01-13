package embedding.chunking

import org.slf4j.LoggerFactory

/**
 * Chunking strategy for plain text documents.
 * Uses paragraph-aware splitting with overlap to maintain semantic coherence.
 *
 * @property maxTokens Maximum tokens per chunk (default: 500)
 * @property overlapTokens Overlap between consecutive chunks (default: 100)
 */
class PlainTextChunkingStrategy(
    private val maxTokens: Int = 500,
    private val overlapTokens: Int = 100
) : ChunkingStrategy {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun splitIntoChunks(
        content: String,
        metadata: DocumentMetadata
    ): List<ChunkWithMetadata> {
        val chunks = mutableListOf<ChunkWithMetadata>()

        // Split into paragraphs (by double newline or single newline)
        val paragraphs = content.split(Regex("\n+")).filter { it.trim().isNotEmpty() }

        if (paragraphs.isEmpty()) {
            logger.warn("No paragraphs found in text for file: ${metadata.fileName}")
            return emptyList()
        }

        logger.debug("Found ${paragraphs.size} paragraphs in text")

        // Standard estimate: 1 token ≈ 0.75 words
        val wordsPerChunk = (maxTokens * 0.75).toInt()
        val overlapWords = (overlapTokens * 0.75).toInt()

        var currentChunk = StringBuilder()
        var currentWordCount = 0
        var previousParagraphs = mutableListOf<String>()

        for (paragraph in paragraphs) {
            val paragraphWords = paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }.size

            // If adding this paragraph exceeds the limit, save current chunk and start new one
            if (currentWordCount > 0 && currentWordCount + paragraphWords > wordsPerChunk) {
                // Save current chunk
                val chunkText = currentChunk.toString().trim()
                if (chunkText.isNotEmpty()) {
                    chunks.add(
                        ChunkWithMetadata(
                            text = chunkText,
                            estimatedTokenCount = estimateTokenCount(chunkText)
                        )
                    )
                }

                // Start new chunk with overlap from previous paragraphs
                currentChunk = StringBuilder()
                currentWordCount = 0

                // Add overlap: take last few paragraphs that fit in overlap size
                val overlapParagraphs = mutableListOf<String>()
                var overlapCount = 0
                for (i in previousParagraphs.size - 1 downTo 0) {
                    val pWords = previousParagraphs[i].split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                    if (overlapCount + pWords <= overlapWords) {
                        overlapParagraphs.add(0, previousParagraphs[i])
                        overlapCount += pWords
                    } else {
                        break
                    }
                }

                // Add overlap paragraphs to new chunk
                for (overlapPara in overlapParagraphs) {
                    currentChunk.append(overlapPara).append("\n")
                    currentWordCount += overlapPara.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                }

                previousParagraphs.clear()
                previousParagraphs.addAll(overlapParagraphs)
            }

            // Add current paragraph to chunk
            currentChunk.append(paragraph).append("\n")
            currentWordCount += paragraphWords
            previousParagraphs.add(paragraph)
        }

        // Add the last chunk if any
        val lastChunk = currentChunk.toString().trim()
        if (lastChunk.isNotEmpty()) {
            chunks.add(
                ChunkWithMetadata(
                    text = lastChunk,
                    estimatedTokenCount = estimateTokenCount(lastChunk)
                )
            )
        }

        logger.debug("Created ${chunks.size} chunks from ${paragraphs.size} paragraphs")
        return chunks
    }

    /**
     * Estimate token count from text.
     * Simple heuristic: count words and divide by 0.75
     */
    private fun estimateTokenCount(text: String): Int {
        val wordCount = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        return (wordCount / 0.75).toInt()
    }
}
