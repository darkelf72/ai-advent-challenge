package sber

import ApiClientInterface
import dto.ApiResponse
import dto.ApiResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import sber.dto.GigaChatMessage
import sber.dto.GigaChatRequest
import sber.dto.GigaChatResponse
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.KeyStore
import java.util.*
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@Serializable
data class OAuthTokenResponse(
    val access_token: String,
    val expires_at: Long
)

class GigaChatApiClient : ApiClientInterface {
    private val logger = LoggerFactory.getLogger(GigaChatApiClient::class.java)
    private companion object {
        val apiKey: String = System.getProperty("gigaChatApiKey")
        const val BASE_URL = "https://gigachat.devices.sberbank.ru/api/v1//chat/completions"
        const val AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
    }

    private var temperature: Double = 0.7

    private var systemPrompt: String = """
        Ты - генеративная языковая модель
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
        engine {
            endpoint {
                connectTimeout = 30000
                requestTimeout = 60000
            }
            https {
                this.trustManager = trustManagerFactory.trustManagers[0] as X509TrustManager
            }
        }
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

    override fun sendRequest(query: String): ApiResponse = runBlocking {
        sendRequestAsync(query)
    }

    private suspend fun sendRequestAsync(userPrompt: String): ApiResponse =
        try {
            val userMessage = GigaChatMessage(role = "user", content = userPrompt)
            messageHistory.add(userMessage)

            val token = getAccessToken()
            val request = GigaChatRequest(
                model = "GigaChat",
                messages = listOf(GigaChatMessage(role = "system", content = systemPrompt)) + messageHistory,
                temperature = temperature
            )

            println("Sending POST request to: $BASE_URL\n$request")
            val startTime = System.currentTimeMillis()
            val response: HttpResponse = httpClient.post(BASE_URL) {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, "application/json")
                setBody(request)
            }
            val endTime = System.currentTimeMillis()
            val executionTime = endTime - startTime
            println("Status: ${response.status}")
            val body = response.body<GigaChatResponse>()
            println(body)
            val answer = body.choices.first().message.content
            val assistantMessage = GigaChatMessage(role = "assistant", content = answer)
            messageHistory.add(assistantMessage)
            val apiResult = ApiResult(
                elapsedTime = executionTime,
                totalTokens = body.usage.total_tokens,
                cost = BigDecimal(1500.0 / 1000000.0 * body.usage.total_tokens).setScale(2, RoundingMode.HALF_UP).toDouble()
            )
            ApiResponse(message = answer, result = Json.encodeToString(apiResult))
        } catch (e: Exception) {
            ApiResponse("Request failed", e.localizedMessage)
        }
}
