package embedding

import embedding.dto.EmbeddingRequest
import embedding.dto.EmbeddingResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class OllamaClient(
    private val baseUrl: String = "http://localhost:11434"
) {

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
        val response: EmbeddingResponse = client
            .post("$baseUrl/api/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(EmbeddingRequest(model, input))
            }
            .body()
        println("embedding: ${response.embedding}")
        return response.embedding
    }

    fun close() = client.close()
}
