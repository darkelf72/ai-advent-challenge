package embedding.service

import embedding.OllamaClient
import embedding.model.TextChunk
import embedding.repository.VectorStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest

/**
 * Service for processing documents and creating embeddings
 * Handles file loading, chunking, embedding creation, and storage
 */
class DocumentEmbeddingService(
    private val ollamaClient: OllamaClient,
    private val vectorStoreRepository: VectorStoreRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
//        private const val EMBEDDING_MODEL = "nomic-embed-text"
        private const val EMBEDDING_MODEL = "zylonai/multilingual-e5-large"
        private const val MAX_TOKENS_PER_CHUNK = 500
        private const val OVERLAP_TOKENS = 50
        private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB
    }

    /**
     * Process document: load, chunk, embed, and store
     * @param file File to process
     * @param onProgress Callback for progress updates (current, total)
     * @return Result with document ID or error
     */
    suspend fun processDocument(
        file: File,
        onProgress: suspend (Int, Int) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.info("Starting document processing: ${file.name}")

            // 1. Validate file
            validateFile(file)

            // 2. Load file content
            val fileContent = file.readText(Charsets.UTF_8)
            val fileHash = calculateFileHash(fileContent)

            logger.info("File loaded: ${file.name}, size=${file.length()} bytes, hash=$fileHash")

            // 3. Check for duplicates and delete if exists
            vectorStoreRepository.findByFileHash(fileHash)?.let { existingDoc ->
                logger.info("Document already exists, removing old index: ${existingDoc.fileName} (id=${existingDoc.id})")
                vectorStoreRepository.deleteDocument(existingDoc.id)
            }

            // 4. Split into chunks
            val chunks = splitIntoChunks(fileContent, MAX_TOKENS_PER_CHUNK, OVERLAP_TOKENS)
            logger.info("Split document into ${chunks.size} chunks")

            // 5. Create document record
            val documentId = vectorStoreRepository.createDocument(
                fileName = file.name,
                filePath = file.absolutePath,
                fileHash = fileHash,
                fileSizeBytes = file.length().toInt(),
                totalChunks = chunks.size,
                embeddingModel = EMBEDDING_MODEL
            )

            logger.info("Created document record with id=$documentId")

            // 6. Process each chunk
            try {
                chunks.forEachIndexed { index, chunk ->
                    logger.debug("Processing chunk ${index + 1}/${chunks.size}")

                    // Create embedding via OllamaClient
                    val embedding = try {
                        ollamaClient.embedding(EMBEDDING_MODEL, chunk.text)
                    } catch (e: Exception) {
                        logger.error("Failed to create embedding for chunk $index", e)
                        throw EmbeddingException("Failed to create embedding for chunk $index", e)
                    }

                    // Save chunk with embedding
                    vectorStoreRepository.saveChunk(
                        documentId = documentId,
                        chunkIndex = index,
                        chunkText = chunk.text,
                        embedding = embedding,
                        tokenCount = chunk.estimatedTokenCount
                    )

                    // Report progress
                    onProgress(index + 1, chunks.size)
                    logger.debug("Saved chunk ${index + 1}/${chunks.size}")
                }

                logger.info("Document processed successfully: id=$documentId, chunks=${chunks.size}")
                Result.success(documentId)

            } catch (e: Exception) {
                logger.error("Failed to process chunks, rolling back document $documentId", e)
                // Rollback: delete the document and all its chunks
                try {
                    vectorStoreRepository.deleteDocument(documentId)
                } catch (rollbackException: Exception) {
                    logger.error("Failed to rollback document $documentId", rollbackException)
                }
                throw e
            }

        } catch (e: Exception) {
            logger.error("Error processing document: ${file.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Validate file before processing
     */
    private fun validateFile(file: File) {
        if (!file.exists()) {
            throw FileNotFoundException("File not found: ${file.absolutePath}")
        }

        if (!file.canRead()) {
            throw SecurityException("Cannot read file: ${file.absolutePath}")
        }

        if (file.length() > MAX_FILE_SIZE_BYTES) {
            throw FileTooLargeException(
                "File size ${file.length()} bytes exceeds maximum allowed $MAX_FILE_SIZE_BYTES bytes (10 MB)"
            )
        }

        if (file.length() == 0L) {
            throw EmptyFileException("File is empty: ${file.name}")
        }
    }

    /**
     * Split text into chunks with overlap
     * Uses sentence and paragraph-aware splitting for better semantic coherence
     */
    private fun splitIntoChunks(
        text: String,
        maxTokens: Int,
        overlapTokens: Int
    ): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()

        // Split into paragraphs (by double newline or single newline)
        val paragraphs = text.split(Regex("\n+")).filter { it.trim().isNotEmpty() }

        if (paragraphs.isEmpty()) {
            logger.warn("No paragraphs found in text")
            return emptyList()
        }

        logger.debug("Found ${paragraphs.size} paragraphs in text")

        // For Russian: 1 token ≈ 0.5 words (Cyrillic uses more tokens)
        val wordsPerChunk = (maxTokens * 0.5).toInt()
        val overlapWords = (overlapTokens * 0.5).toInt()

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
                        TextChunk(
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
                TextChunk(
                    text = lastChunk,
                    estimatedTokenCount = estimateTokenCount(lastChunk)
                )
            )
        }

        logger.debug("Created ${chunks.size} chunks from ${paragraphs.size} paragraphs")
        return chunks
    }

    /**
     * Estimate token count from text
     * Simple heuristic: count words and divide by 0.75
     */
    private fun estimateTokenCount(text: String): Int {
        val wordCount = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        return (wordCount / 0.75).toInt()
    }

    /**
     * Calculate SHA-256 hash of file content
     */
    private fun calculateFileHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

// Custom exceptions

class EmbeddingException(message: String, cause: Throwable? = null) : Exception(message, cause)
class FileTooLargeException(message: String) : Exception(message)
class EmptyFileException(message: String) : Exception(message)
class FileNotFoundException(message: String) : Exception(message)
