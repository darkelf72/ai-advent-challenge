package embedding

import embedding.dto.EmbeddingRequest
import embedding.dto.EmbeddingResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class OllamaClient(
    private val baseUrl: String = "http://localhost:11434"
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        // nomic-embed-text has context length of 8192 tokens
        // Using conservative estimate: 1 token ≈ 0.75 words
        private const val MAX_INPUT_TOKENS = 8192
        private const val SAFE_MAX_WORDS = 6000 // 8192 * 0.75 = 6144, use 6000 for safety
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }

    suspend fun embedding(model: String, input: String): List<Float> {
        // Validate input length before sending to prevent context length errors
        val wordCount = input.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        if (wordCount > SAFE_MAX_WORDS) {
            val estimatedTokens = (wordCount / 0.75).toInt()
            logger.warn("Input text is very long: ~$estimatedTokens tokens ($wordCount words). Model limit is $MAX_INPUT_TOKENS tokens.")
            throw OllamaException(
                "Input text is too long: approximately $estimatedTokens tokens ($wordCount words). " +
                "Model '$model' has a maximum context length of $MAX_INPUT_TOKENS tokens. " +
                "Please reduce chunk size in chunking strategy."
            )
        }

        try {
            val httpResponse = client.post("$baseUrl/api/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(EmbeddingRequest(model, input))
            }

            // Log response status and body for debugging
            val statusCode = httpResponse.status.value
            val rawBody = httpResponse.bodyAsText()

            logger.debug("Ollama API response status: $statusCode")
            logger.debug("Ollama API response body (first 500 chars): ${rawBody.take(500)}")

            // Parse response
            val response: EmbeddingResponse = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }.decodeFromString(rawBody)

            // Check for errors
            if (response.error != null) {
                throw OllamaException("Ollama API error: ${response.error}")
            }

            if (response.embedding == null) {
                logger.error("Ollama API returned null embedding. Full response: $rawBody")
                throw OllamaException("Ollama API returned null embedding. This might indicate that the model is not loaded or Ollama is not running properly.")
            }

            return response.embedding
        } catch (e: OllamaException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to get embedding from Ollama", e)
            throw OllamaException("Failed to connect to Ollama API at $baseUrl. Make sure Ollama is running and the model '$model' is loaded. Error: ${e.message}", e)
        }
    }

    fun close() = client.close()
}

class OllamaException(message: String, cause: Throwable? = null) : Exception(message, cause)
