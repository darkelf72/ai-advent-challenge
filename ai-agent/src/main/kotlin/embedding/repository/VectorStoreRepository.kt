package embedding.repository

import embedding.model.DocumentInfo
import embedding.model.DocumentChunk

/**
 * Repository interface for vector store operations
 * This abstraction allows easy migration from SQLite to dedicated vector databases
 * like Qdrant, Milvus, or Pinecone in the future
 */
interface VectorStoreRepository {

    /**
     * Find document by file hash
     * @param fileHash SHA-256 hash of the file content
     * @return DocumentInfo or null if not found
     */
    fun findByFileHash(fileHash: String): DocumentInfo?

    /**
     * Create new document record
     * @param fileName Name of the file
     * @param filePath Path to the file
     * @param fileHash SHA-256 hash of the file
     * @param fileSizeBytes Size of file in bytes
     * @param totalChunks Total number of chunks
     * @param embeddingModel Name of the embedding model used
     * @return Created document ID
     */
    fun createDocument(
        fileName: String,
        filePath: String,
        fileHash: String,
        fileSizeBytes: Int,
        totalChunks: Int,
        embeddingModel: String
    ): Int

    /**
     * Delete document and all its chunks
     * @param documentId Document ID to delete
     */
    fun deleteDocument(documentId: Int)

    /**
     * Save chunk with embedding
     * @param documentId Parent document ID
     * @param chunkIndex Index of chunk in document
     * @param chunkText Text content of chunk
     * @param embedding Embedding vector as list of floats
     * @param tokenCount Number of tokens in chunk
     */
    fun saveChunk(
        documentId: Int,
        chunkIndex: Int,
        chunkText: String,
        embedding: List<Float>,
        tokenCount: Int
    )

    /**
     * Get all chunks for a document
     * @param documentId Document ID
     * @return List of chunks
     */
    fun getChunksByDocumentId(documentId: Int): List<DocumentChunk>

    /**
     * Get all documents
     * @return List of all documents
     */
    fun getAllDocuments(): List<DocumentInfo>
}
