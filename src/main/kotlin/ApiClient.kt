import dto.CompletionOptionsDto
import dto.MessageDto
import dto.RequestDto
import dto.ResponseDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking

class ApiClient {
    companion object {
        const val URL = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
        val apiKey: String = System.getProperty("yandexApiKey")
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    fun sendRequest(query: String): String =
        runBlocking {
            try {
                println("Sending GET request to: $URL")
                val request = RequestDto(
                    modelUri = "gpt://b1g2vhjdd9rgjq542poc/yandexgpt/latest",
                    completionOptions = CompletionOptionsDto(
                        stream = false,
                        temperature = 0.5,
                        maxTokens = 100
                    ),
                    messages = listOf(
                        MessageDto(
                            role = "system",
                            text =  """
                                Возвращай ответ в формате json.
                                Ответ должен состоять из двух полей:
                                1) message c текстом ответа 
                                2) elapsedTime со временем в миллисекундах, затраченным на получение ответа
                            """.trimIndent()
                        ),
                        MessageDto(
                            role = "user",
                            text = query
                        )
                    )
                )

                val response: HttpResponse = client.post(URL) {
                    header("accept", "application/json")
                    header("content-type", "application/json")
                    header("Authorization", "Api-Key $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                println("Status: ${response.status}")
                val body = response.body<ResponseDto>()
                val answer = body.result.alternatives.first().message.text
                answer
            } catch (e: Exception) {
                "Request failed: ${e.message}"
            } finally {
                client.close()
            }
        }
    }