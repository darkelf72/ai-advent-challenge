package embedding.chunking

import org.slf4j.LoggerFactory

/**
 * Chunking strategy for Markdown documents.
 * Intelligently splits documents by headings while preserving hierarchical structure.
 *
 * @property maxTokens Maximum tokens per chunk (default: 500)
 * @property overlapTokens Overlap between consecutive chunks (default: 100)
 */
class MarkdownChunkingStrategy(
    private val maxTokens: Int = 500,
    private val overlapTokens: Int = 100
) : ChunkingStrategy {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val headingRegex = Regex("^(#{1,6})\\s+(.+)$")

    override fun splitIntoChunks(
        content: String,
        metadata: DocumentMetadata
    ): List<ChunkWithMetadata> {
        logger.debug("Starting Markdown chunking for file: ${metadata.fileName}")

        // 1. Parse Markdown structure
        val sections = parseMarkdownSections(content)
        logger.debug("Parsed ${sections.size} sections from Markdown")

        // 2. Smart chunking by sections
        val chunks = mutableListOf<ChunkWithMetadata>()

        for (section in sections) {
            if (section.estimatedTokens <= maxTokens) {
                // Section fits entirely in one chunk
                chunks.add(section.toChunk())
            } else {
                // Section is too large, need to split
                chunks.addAll(splitLargeSection(section))
            }
        }

        // 3. Add contextual overlap between chunks
        val chunksWithOverlap = addContextualOverlap(chunks)

        logger.debug("Created ${chunksWithOverlap.size} chunks from ${sections.size} sections")
        return chunksWithOverlap
    }

    /**
     * Parse Markdown content into sections based on headings.
     */
    private fun parseMarkdownSections(content: String): List<MarkdownSection> {
        val lines = content.lines()
        val sections = mutableListOf<MarkdownSection>()

        var currentSection: MarkdownSection? = null
        val headingStack = mutableListOf<String>() // Stack of headings for hierarchy

        for ((lineIndex, line) in lines.withIndex()) {
            val headingMatch = headingRegex.matchEntire(line)

            when {
                headingMatch != null -> {
                    // Save previous section
                    currentSection?.let { sections.add(it) }

                    // Parse heading
                    val level = headingMatch.groupValues[1].length
                    val heading = headingMatch.groupValues[2].trim()

                    // Update heading stack
                    updateHeadingStack(headingStack, level, heading)

                    // Create new section
                    currentSection = MarkdownSection(
                        heading = line,
                        level = level,
                        headingPath = headingStack.toList(),
                        startLine = lineIndex
                    )
                }
                else -> {
                    // Add line to current section (or create default section if no heading yet)
                    if (currentSection == null) {
                        // Document starts without heading - create default section
                        currentSection = MarkdownSection(
                            heading = "",
                            level = 0,
                            headingPath = emptyList(),
                            startLine = lineIndex
                        )
                    }
                    currentSection.addLine(line)
                }
            }
        }

        // Add the last section
        currentSection?.let { sections.add(it) }

        return sections
    }

    /**
     * Update heading stack based on heading level.
     * Removes headings of equal or lower level before adding new one.
     */
    private fun updateHeadingStack(stack: MutableList<String>, level: Int, heading: String) {
        // Remove headings at or below this level
        while (stack.size >= level) {
            stack.removeAt(stack.lastIndex)
        }
        stack.add(heading)
    }

    /**
     * Split a large section into smaller chunks while preserving heading context.
     */
    private fun splitLargeSection(section: MarkdownSection): List<ChunkWithMetadata> {
        val chunks = mutableListOf<ChunkWithMetadata>()

        // Split content by paragraphs
        val paragraphs = section.content.toString()
            .split(Regex("\n\n+"))
            .filter { it.trim().isNotEmpty() }

        if (paragraphs.isEmpty()) {
            // Section has only heading, no content
            return listOf(section.toChunk())
        }

        var currentChunk = StringBuilder()
        // Start each chunk with the heading
        if (section.heading.isNotEmpty()) {
            currentChunk.append(section.heading).append("\n\n")
        }
        var currentTokens = estimateTokens(section.heading)

        for (paragraph in paragraphs) {
            val paraTokens = estimateTokens(paragraph)

            // Check if adding this paragraph would exceed the limit
            if (currentTokens + paraTokens > maxTokens && currentChunk.isNotEmpty()) {
                // Save current chunk
                chunks.add(createChunk(currentChunk.toString().trim(), section))

                // Start new chunk with heading
                currentChunk = StringBuilder()
                if (section.heading.isNotEmpty()) {
                    currentChunk.append(section.heading).append("\n\n")
                }
                currentTokens = estimateTokens(section.heading)
            }

            currentChunk.append(paragraph).append("\n\n")
            currentTokens += paraTokens
        }

        // Add the last chunk
        if (currentChunk.isNotEmpty()) {
            chunks.add(createChunk(currentChunk.toString().trim(), section))
        }

        return chunks
    }

    /**
     * Add contextual overlap between chunks based on heading hierarchy.
     */
    private fun addContextualOverlap(chunks: List<ChunkWithMetadata>): List<ChunkWithMetadata> {
        if (chunks.size <= 1) return chunks

        val result = mutableListOf<ChunkWithMetadata>()
        result.add(chunks[0])

        for (i in 1 until chunks.size) {
            val currentChunk = chunks[i]
            val previousChunk = chunks[i - 1]

            // Add overlap only if chunks share the same heading path (same section)
            if (sharesHeadingPath(currentChunk, previousChunk)) {
                val overlapText = extractOverlapText(previousChunk.text)
                if (overlapText.isNotEmpty()) {
                    val combinedText = "$overlapText\n\n${currentChunk.text}"
                    result.add(
                        currentChunk.copy(
                            text = combinedText,
                            estimatedTokenCount = estimateTokens(combinedText)
                        )
                    )
                } else {
                    result.add(currentChunk)
                }
            } else {
                // Different sections - no overlap needed
                result.add(currentChunk)
            }
        }

        return result
    }

    /**
     * Check if two chunks share the same heading hierarchy.
     */
    private fun sharesHeadingPath(chunk1: ChunkWithMetadata, chunk2: ChunkWithMetadata): Boolean {
        val path1 = chunk1.metadata.headingPath
        val path2 = chunk2.metadata.headingPath

        if (path1.isEmpty() || path2.isEmpty()) return false
        return path1.first() == path2.first()
    }

    /**
     * Extract overlap text from the end of a chunk.
     */
    private fun extractOverlapText(text: String): String {
        val paragraphs = text.split(Regex("\n\n+")).filter { it.trim().isNotEmpty() }
        if (paragraphs.isEmpty()) return ""

        val overlapWords = (overlapTokens * 0.75).toInt()
        val overlapParagraphs = mutableListOf<String>()
        var wordCount = 0

        // Take paragraphs from the end until we reach overlap size
        for (i in paragraphs.size - 1 downTo 0) {
            val para = paragraphs[i]
            val paraWords = para.split(Regex("\\s+")).filter { it.isNotEmpty() }.size

            if (wordCount + paraWords <= overlapWords) {
                overlapParagraphs.add(0, para)
                wordCount += paraWords
            } else {
                break
            }
        }

        return overlapParagraphs.joinToString("\n\n")
    }

    /**
     * Create a chunk from text and section metadata.
     */
    private fun createChunk(text: String, section: MarkdownSection): ChunkWithMetadata {
        return ChunkWithMetadata(
            text = text,
            estimatedTokenCount = estimateTokens(text),
            metadata = ChunkMetadata(
                headingPath = section.headingPath,
                level = section.level,
                startLine = section.startLine
            )
        )
    }

    /**
     * Estimate token count from text.
     * Simple heuristic: count words and divide by 0.75
     */
    private fun estimateTokens(text: String): Int {
        val wordCount = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        return (wordCount / 0.75).toInt()
    }

    /**
     * Internal data class representing a Markdown section.
     */
    private data class MarkdownSection(
        val heading: String,
        val level: Int,
        val headingPath: List<String>,
        val startLine: Int,
        val content: StringBuilder = StringBuilder()
    ) {
        fun addLine(line: String) {
            content.append(line).append("\n")
        }

        val estimatedTokens: Int
            get() = estimateTokens(heading) + estimateTokens(content.toString())

        fun toChunk(): ChunkWithMetadata {
            val fullText = if (heading.isNotEmpty()) {
                buildString {
                    append(heading).append("\n\n")
                    append(content.toString())
                }.trim()
            } else {
                content.toString().trim()
            }

            return ChunkWithMetadata(
                text = fullText,
                estimatedTokenCount = estimateTokens(fullText),
                metadata = ChunkMetadata(
                    headingPath = headingPath,
                    level = level,
                    startLine = startLine
                )
            )
        }

        private fun estimateTokens(text: String): Int {
            val wordCount = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
            return (wordCount / 0.75).toInt()
        }
    }
}
