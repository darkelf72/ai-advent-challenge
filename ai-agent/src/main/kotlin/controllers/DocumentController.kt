package controllers

import dto.ResponseDto
import embedding.service.DocumentEmbeddingService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Controller for document upload and embedding operations
 */
class DocumentController(
    private val embeddingService: DocumentEmbeddingService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // Store progress for active uploads
    private val progressMap = ConcurrentHashMap<String, ProgressInfo>()

    // Store job references for cancellation if needed
    private val jobMap = ConcurrentHashMap<String, Job>()

    companion object {
        private const val UPLOAD_DIR = "./uploads"
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024L // 10 MB
    }

    init {
        // Ensure upload directory exists
        File(UPLOAD_DIR).mkdirs()
    }

    /**
     * Handle document upload and processing
     * POST /api/document/upload
     */
    suspend fun handleUploadDocument(call: ApplicationCall) {
        val requestId = UUID.randomUUID().toString()
        logger.info("Received document upload request: requestId=$requestId")

        try {
            val multipart = call.receiveMultipart()
            var file: File? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        val fileName = part.originalFileName ?: "unknown"
                        logger.info("Processing file upload: $fileName")

                        // Validate file name
                        if (!fileName.endsWith(".txt")) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ResponseDto(
                                    success = false,
                                    message = "Only .txt files are supported",
                                    data = null
                                )
                            )
                            part.dispose()
                            return@forEachPart
                        }

                        // Save uploaded file to temp location
                        val tempFile = File(UPLOAD_DIR, "${requestId}_$fileName")
                        part.streamProvider().use { input ->
                            tempFile.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Check file size
                        if (tempFile.length() > MAX_FILE_SIZE) {
                            tempFile.delete()
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ResponseDto(
                                    success = false,
                                    message = "File size exceeds maximum allowed size of 10 MB",
                                    data = null
                                )
                            )
                            part.dispose()
                            return@forEachPart
                        }

                        file = tempFile
                        part.dispose()
                    }
                    else -> part.dispose()
                }
            }

            // Check if file was uploaded
            if (file == null) {
                logger.warn("No file found in upload request: requestId=$requestId")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ResponseDto(
                        success = false,
                        message = "No file uploaded",
                        data = null
                    )
                )
                return
            }

            // Initialize progress
            progressMap[requestId] = ProgressInfo(
                current = 0,
                total = 0,
                status = ProcessingStatus.PROCESSING,
                error = null
            )

            // Start processing in background
            val job = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = embeddingService.processDocument(file) { current, total ->
                        progressMap[requestId] = ProgressInfo(
                            current = current,
                            total = total,
                            status = ProcessingStatus.PROCESSING,
                            error = null
                        )
                    }

                    if (result.isSuccess) {
                        progressMap[requestId] = ProgressInfo(
                            current = progressMap[requestId]?.total ?: 0,
                            total = progressMap[requestId]?.total ?: 0,
                            status = ProcessingStatus.COMPLETED,
                            error = null
                        )
                        logger.info("Document processing completed: requestId=$requestId, documentId=${result.getOrNull()}")
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        progressMap[requestId] = ProgressInfo(
                            current = 0,
                            total = 0,
                            status = ProcessingStatus.FAILED,
                            error = error
                        )
                        logger.error("Document processing failed: requestId=$requestId, error=$error")
                    }

                } catch (e: Exception) {
                    logger.error("Exception during document processing: requestId=$requestId", e)
                    progressMap[requestId] = ProgressInfo(
                        current = 0,
                        total = 0,
                        status = ProcessingStatus.FAILED,
                        error = e.message ?: "Unknown error"
                    )
                } finally {
                    // Clean up uploaded file
                    file.delete()
                    jobMap.remove(requestId)
                }
            }

            jobMap[requestId] = job

            // Respond immediately with request ID
            call.respond(
                HttpStatusCode.OK,
                ResponseDto(
                    success = true,
                    message = "Document upload started",
                    data = mapOf("requestId" to requestId)
                )
            )

        } catch (e: Exception) {
            logger.error("Error handling document upload: requestId=$requestId", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ResponseDto(
                    success = false,
                    message = "Failed to process upload: ${e.message}",
                    data = null
                )
            )
        }
    }

    /**
     * Get progress for a document processing request
     * GET /api/document/progress/{requestId}
     */
    suspend fun handleGetProgress(call: ApplicationCall) {
        val requestId = call.parameters["requestId"]

        if (requestId == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                ResponseDto(
                    success = false,
                    message = "Request ID is required",
                    data = null
                )
            )
            return
        }

        val progress = progressMap[requestId]

        if (progress == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ResponseDto(
                    success = false,
                    message = "Request ID not found",
                    data = null
                )
            )
            return
        }

        val percentage = if (progress.total > 0) {
            (progress.current.toFloat() / progress.total * 100).toInt()
        } else {
            0
        }

        call.respond(
            HttpStatusCode.OK,
            ResponseDto(
                success = true,
                message = "Progress retrieved",
                data = mapOf(
                    "current" to progress.current,
                    "total" to progress.total,
                    "percentage" to percentage,
                    "status" to progress.status.name,
                    "error" to progress.error
                )
            )
        )

        // Clean up completed or failed requests after returning progress
        if (progress.status == ProcessingStatus.COMPLETED || progress.status == ProcessingStatus.FAILED) {
            // Wait a bit before cleaning up to allow client to retrieve final status
            CoroutineScope(Dispatchers.IO).launch {
                delay(5000) // 5 seconds
                progressMap.remove(requestId)
            }
        }
    }
}

/**
 * Processing status enum
 */
enum class ProcessingStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}

/**
 * Progress information for document processing
 */
data class ProgressInfo(
    val current: Int,
    val total: Int,
    val status: ProcessingStatus,
    val error: String?
)
