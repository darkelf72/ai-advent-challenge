package sber

import ApiClientInterface
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.security.KeyStore
import java.util.*
import javax.net.ssl.TrustManagerFactory

@Serializable
data class GigaChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class GigaChatRequest(
    val model: String,
    val messages: List<GigaChatMessage>,
    val temperature: Double = 0.7,
    val top_p: Double = 0.9,
    val max_tokens: Int = 1024
)

@Serializable
data class GigaChatChoice(
    val message: GigaChatMessage,
    val index: Int,
    val finish_reason: String
)

@Serializable
data class GigaChatResponse(
    val choices: List<GigaChatChoice>,
    val created: Long,
    val model: String,
    val object_type: String? = null
)

@Serializable
data class OAuthTokenResponse(
    val access_token: String,
    val expires_at: Long
)

class GigaChatApiClient : ApiClientInterface {
    private val logger = LoggerFactory.getLogger(GigaChatApiClient::class.java)
    private companion object {
        val apiKey: String = System.getProperty("gigaChatApiKey")
        const val BASE_URL = "https://gigachat.devices.sberbank.ru/api/v1"
        const val AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
    }

    private var temperature: Double = 0.7

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

    private val messageHistory = mutableListOf<GigaChatMessage>()

    override fun getSystemPrompt(): String = systemPrompt

    override fun setSystemPrompt(prompt: String) {
        systemPrompt = prompt
    }

    override fun clearMessages() {
        messageHistory.clear()
    }

    override fun getTemperature(): Double = temperature

    override fun setTemperature(temperature: Double) {
        this.temperature = temperature.coerceIn(0.0, 1.0)
    }

    private val keystoreStream = this::class.java.classLoader.getResourceAsStream("truststore.jks")
    private val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
        init(KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(keystoreStream, "changeit".toCharArray())
        })
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
//        engine {
//            endpoint {
//                connectTimeout = 30000
//                requestTimeout = 60000
//            }
//            https {
//                this.trustManager = trustManagerFactory.trustManagers[0] as X509TrustManager
//            }
//        }
    }

    private var accessToken: String? = null
    private var tokenExpiresAt: Long = 0

    private suspend fun getAccessToken(): String {
        val currentTime = System.currentTimeMillis()
        if (accessToken != null && currentTime < tokenExpiresAt) return accessToken!!

        logger.info("Obtaining new access token from GigaChat")
        val response: HttpResponse = httpClient.post(AUTH_URL) {
            headers {
                append(HttpHeaders.Authorization, "Basic $apiKey")
                append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                append("RqUID", UUID.randomUUID().toString())
            }
            setBody("scope=GIGACHAT_API_PERS")
        }
        if (response.status.isSuccess()) {
            val tokenResponse: OAuthTokenResponse = response.body()
            accessToken = tokenResponse.access_token
            tokenExpiresAt = tokenResponse.expires_at
            logger.info("Successfully obtained access token")
            return accessToken!!
        } else {
            val errorBody = response.bodyAsText()
            logger.error("Failed to obtain access token: ${response.status} - $errorBody")
            throw Exception("Failed to obtain access token: ${response.status}")
        }
    }

    override fun sendRequest(userPrompt: String): String = runBlocking {
        sendRequestAsync(userPrompt)
    }

    private suspend fun sendRequestAsync(userPrompt: String): String {
        try {
            val userMessage = GigaChatMessage(role = "user", content = userPrompt)
            messageHistory.add(userMessage)

            val token = getAccessToken()
            val request = GigaChatRequest(
                model = "GigaChat",
                messages = listOf(GigaChatMessage(role = "system", content = systemPrompt)) + messageHistory,
                temperature = temperature
            )

            val response: HttpResponse = httpClient.post("$BASE_URL/chat/completions") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val chatResponse: GigaChatResponse = response.body()
                val answer = chatResponse.choices.firstOrNull()?.message?.content
                    ?: "Получен пустой ответ от GigaChat"
                messageHistory.add(GigaChatMessage(role = "assistant", content = answer))
                return answer
            } else {
                val errorBody = response.bodyAsText()
                logger.error("GigaChat API error: ${response.status} - $errorBody")
                throw Exception("GigaChat API error: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Error communicating with GigaChat API", e)
            throw e
        }
    }
}
