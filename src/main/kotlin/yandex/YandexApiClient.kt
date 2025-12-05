package yandex

import ApiClientInterface
import yandex.dto.CompletionOptionsDto
import yandex.dto.MessageDto
import yandex.dto.RequestDto
import yandex.dto.ResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking

class YandexApiClient : ApiClientInterface {
    private companion object {
        const val URL = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
        val apiKey: String = System.getProperty("yandexApiKey")
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    private var systemPrompt: String = """
        Ты — профессиональный технический писатель. Твоя задача: собрать требования для технического задания (ТЗ) по проекту «[название проекта]».

        **Процесс работы:**
        1. Ты должен задать эти уточняющие вопросы, чтобы поэтапно выявить все ключевые требования:
           - Какая цель проекта
           - Какой срок проект
           - Какой бюджет проекта
        2. Каждый раз задавай только один вопрос.
        3. Когда эти вопросы будут заданы, самостоятельно заверши диалог и выдай итоговое ТЗ в формате Markdown.

        **Критерии достаточности данных (когда нужно остановиться и выдать ТЗ):**
        - Определена цель проекта.
        - Указаны сроки и бюджет (если известны).

        **Формат итогового ТЗ:**
        ```markdown
        # Техническое задание: [название проекта]

        ## 1. Цель проекта
        [Текст]

        ## 2. Сроки и бюджет
        - Сроки: [срок]
        - Бюджет: [сумма]
    """.trimIndent()

    private val messageHistory = mutableListOf<MessageDto>()

    override fun getSystemPrompt(): String = systemPrompt

    override fun setSystemPrompt(prompt: String) {
        systemPrompt = prompt
    }

    override fun clearMessages() {
        messageHistory.clear()
    }

    override fun sendRequest(query: String): String =
        runBlocking {
            try {
                val userMessage = MessageDto(role = "user", text = query)
                messageHistory.add(userMessage)

                val request = RequestDto(
                    modelUri = "gpt://b1g2vhjdd9rgjq542poc/yandexgpt/latest",
                    completionOptions = CompletionOptionsDto(
                        stream = false,
                        temperature = 0.5,
                        maxTokens = 100
                    ),
                    messages = listOf(MessageDto("system", systemPrompt)) + messageHistory
                )

                println("Sending POST request to: $URL\n$request")
                val response: HttpResponse = client.post(URL) {
                    header("accept", "application/json")
                    header("content-type", "application/json")
                    header("Authorization", "Api-Key $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                println("Status: ${response.status}")
                val body = response.body<ResponseDto>()
                println(body)
                val answer = body.result.alternatives.first().message.text
                val assistantMessage = MessageDto(role = "assistant", text = answer)
                messageHistory.add(assistantMessage)
                answer
            } catch (e: Exception) {
                "Request failed: ${e.message}"
            }
        }
    }