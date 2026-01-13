package embedding.service

import embedding.OllamaClient
import embedding.OllamaException
import embedding.chunking.ChunkingStrategyFactory
import embedding.chunking.DocumentMetadata
import embedding.chunking.UnsupportedFileTypeException
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
        // Note: Actual chunking parameters are now controlled by ChunkingStrategyFactory
        // These constants remain for reference and file size validation
        private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB
    }

    /**
     * Process document: load, chunk, embed, and store
     * @param file File to process
     * @param originalFileName Original filename without UUID prefix (optional, defaults to file.name)
     * @param onProgress Callback for progress updates (current, total)
     * @return Result with document ID or error
     */
    suspend fun processDocument(
        file: File,
        originalFileName: String? = null,
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

            // 4. Determine file extension and get appropriate chunking strategy
            val fileExtension = file.extension
            val chunkingStrategy = ChunkingStrategyFactory.getStrategy(fileExtension)

            val metadata = DocumentMetadata(
                fileName = file.name,
                fileExtension = fileExtension,
                originalName = originalFileName
            )

            // 5. Split into chunks using strategy
            val chunks = chunkingStrategy.splitIntoChunks(fileContent, metadata)

            // Log chunk statistics
            val avgTokens = if (chunks.isNotEmpty()) chunks.map { it.estimatedTokenCount }.average() else 0.0
            val maxTokens = chunks.maxOfOrNull { it.estimatedTokenCount } ?: 0
            val minTokens = chunks.minOfOrNull { it.estimatedTokenCount } ?: 0
            logger.info("Split document into ${chunks.size} chunks using ${chunkingStrategy::class.simpleName}")
            logger.info("Chunk token statistics: min=$minTokens, max=$maxTokens, avg=${avgTokens.toInt()}")

            // 6. Create document record
            val documentId = vectorStoreRepository.createDocument(
                fileName = file.name,
                filePath = file.absolutePath,
                fileHash = fileHash,
                fileSizeBytes = file.length().toInt(),
                totalChunks = chunks.size,
                embeddingModel = EMBEDDING_MODEL,
                name = originalFileName ?: file.name
            )

            logger.info("Created document record with id=$documentId")

            // 7. Process each chunk
            try {
                chunks.forEachIndexed { index, chunk ->
                    val wordCount = chunk.text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                    logger.debug("Processing chunk ${index + 1}/${chunks.size}: ${chunk.estimatedTokenCount} tokens (~$wordCount words)")

                    // Create embedding via OllamaClient
                    val embedding = try {
                        ollamaClient.embedding(EMBEDDING_MODEL, chunk.text)
                    } catch (e: OllamaException) {
                        logger.error("Ollama error for chunk $index: ${e.message}", e)
                        throw EmbeddingException("Ollama error for chunk $index: ${e.message}. Please ensure Ollama is running and model '$EMBEDDING_MODEL' is loaded.", e)
                    } catch (e: Exception) {
                        logger.error("Failed to create embedding for chunk $index", e)
                        throw EmbeddingException("Failed to create embedding for chunk $index: ${e.message}", e)
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

        } catch (e: UnsupportedFileTypeException) {
            logger.error("Unsupported file type: ${file.name}", e)
            Result.failure(e)
        } catch (e: OllamaException) {
            logger.error("Ollama error processing document: ${file.name}", e)
            Result.failure(e)
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
