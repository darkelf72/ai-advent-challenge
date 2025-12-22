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
        private const val EMBEDDING_MODEL = "nomic-embed-text"
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
     * Uses word-based splitting with token estimation
     */
    private fun splitIntoChunks(
        text: String,
        maxTokens: Int,
        overlapTokens: Int
    ): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()

        // Split by whitespace and filter empty strings
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

        if (words.isEmpty()) {
            logger.warn("No words found in text")
            return emptyList()
        }

        // Approximate: 1 token ≈ 0.75 words (for English)
        val wordsPerChunk = (maxTokens * 0.75).toInt()
        val overlapWords = (overlapTokens * 0.75).toInt()

        logger.debug("Chunking: wordsPerChunk=$wordsPerChunk, overlapWords=$overlapWords, totalWords=${words.size}")

        var startIndex = 0
        while (startIndex < words.size) {
            val endIndex = minOf(startIndex + wordsPerChunk, words.size)
            val chunkWords = words.subList(startIndex, endIndex)
            val chunkText = chunkWords.joinToString(" ")

            chunks.add(
                TextChunk(
                    text = chunkText,
                    estimatedTokenCount = estimateTokenCount(chunkText)
                )
            )

            // Move to next chunk with overlap
            startIndex += wordsPerChunk - overlapWords

            // Prevent infinite loop
            if (startIndex >= words.size || wordsPerChunk <= overlapWords) {
                break
            }
        }

        logger.debug("Created ${chunks.size} chunks from ${words.size} words")
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
