package di

import apiclients.ApiClientInterface
import apiclients.config.apiClientConfig
import apiclients.gigachat.GigaChatApiClient
import apiclients.yandex.YandexApiClient
import database.repository.ClientConfigRepository
import database.repository.MessageHistoryRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.serialization.json.Json
import mcp.McpClientManager
import mcp.ToolRegistry
import mcp.tools.SaveWeatherToDbExecutor
import mcp.tools.WeatherInCityExecutor
import org.koin.core.qualifier.named
import org.koin.dsl.module
import service.SummarizationService
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

val appModule = module {
    // Repositories
    single { ClientConfigRepository() }
    single { MessageHistoryRepository() }

    // Services
    single { SummarizationService(summarizeApiClient = get(named("summarizeApiClient"))) }

    single<Client>(named("dbMcpClient")) {
        Client(
            clientInfo = Implementation(
                name = "mcp-cli-client",
                version = "1.0.0"
            ),
            options = ClientOptions()
        )
    }

    single<Client>(named("httpMcpClient")) {
        Client(
            clientInfo = Implementation(
                name = "mcp-cli-client",
                version = "1.0.0"
            ),
            options = ClientOptions()
        )
    }

    single<HttpClient>(named("mcpHttpClient")) {
        // Create HTTP client for SSE transport
        HttpClient(CIO) {
            install(SSE)
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
    }

    // Обычный HttpClient для YandexApiClient
    single<HttpClient>(named("standardHttpClient")) {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
            engine {
                endpoint {
                    connectTimeout = 60000
                    requestTimeout = 120000
                }
            }
        }
    }

    // HttpClient с SSL trust manager для GigaChatApiClient
    single<HttpClient>(named("sslHttpClient")) {
        val keystoreStream = this::class.java.classLoader.getResourceAsStream("truststore.jks")
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(keystoreStream, "changeit".toCharArray())
            })
        }

        HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
            engine {
                endpoint {
                    connectTimeout = 60000
                    requestTimeout = 120000
                }
                https {
                    trustManager = trustManagerFactory.trustManagers[0] as X509TrustManager
                }
            }
        }
    }

    val chatApiClientConfig = apiClientConfig {}
    val summarizeApiClientConfig = apiClientConfig {
        temperature = 0.5
        maxTokens = 100
        systemPrompt = """
            Ты — ассистент, способный сжимать историю диалога с генеративной языковой моделью
            На вход ты получишь историю диалога в следующем формате JSON:
            {
              "messages": [
                {"role": "system", "content": "Систменый промпт"},
                {"role": "user", "content": "Первое сообщение пользователя"},
                {"role": "assistant", "content": "Ответ модели"},
                {"role": "user", "content": "Второе сообщение пользователя"},
                {"role": "assistant", "content": "Ответ модели"},
                ...
              ]
            }
            Создай сжатую версию истории диалога, сохранив ключевую информацию и важные детали для последующего использования этой сжатой версии в качестве контекста диалога для системного промпта
            Размер сжатой версии должен быть НЕ БОЛЕЕ $maxTokens токенов!
        """.trimIndent()
    }
    // API Clients
    single<YandexApiClient> {
        YandexApiClient(
            httpClient = get(named("standardHttpClient")),
            apiClientConfig = chatApiClientConfig,
            clientName = "yandex",
            configRepository = get(),
            messageHistoryRepository = get()
        )
    }

    single<GigaChatApiClient> {
        GigaChatApiClient(
            httpClient = get(named("sslHttpClient")),
            apiClientConfig = chatApiClientConfig,
            clientName = "gigachat",
            configRepository = get(),
            messageHistoryRepository = get(),
            toolRegistry = get()
        )
    }

    single<ApiClientInterface>(named("summarizeApiClient")) {
        GigaChatApiClient(
            httpClient = get(named("sslHttpClient")),
            apiClientConfig = summarizeApiClientConfig,
            clientName = "gigachat-summarize",
            configRepository = get(),
            messageHistoryRepository = get(),
            toolRegistry = get()
        )
    }

    // MCP Tool Executors
    single<WeatherInCityExecutor> {
        WeatherInCityExecutor(
            mcpClient = get(named("httpMcpClient"))
        )
    }

    single<SaveWeatherToDbExecutor> {
        SaveWeatherToDbExecutor(
            mcpClient = get(named("dbMcpClient"))
        )
    }

    // MCP Client Manager для ленивого подключения
    single<McpClientManager> {
        val dbMcpServerUrl = System.getenv("DB_MCP_SERVER_URL") ?: "http://localhost:8081"
        val httpMcpServerUrl = System.getenv("HTTP_MCP_SERVER_URL") ?: "http://localhost:8082"

        McpClientManager(
            dbMcpClient = get(named("dbMcpClient")),
            httpMcpClient = get(named("httpMcpClient")),
            mcpHttpClient = get(named("mcpHttpClient")),
            dbMcpServerUrl = dbMcpServerUrl,
            httpMcpServerUrl = httpMcpServerUrl
        )
    }

    // Tool Registry с поддержкой ленивой загрузки
    single<ToolRegistry> {
        ToolRegistry(
            executors = listOf(
                get<WeatherInCityExecutor>(),
                get<SaveWeatherToDbExecutor>()
            ),
            mcpClientManager = get()
        )
    }

    // Мапа всех доступных клиентов
    single<Map<String, ApiClientInterface>>(named("availableClients")) {
        mapOf(
            "YandexGPT Pro 5.1" to get<YandexApiClient>(),
            "GigaChat 2 Lite" to get<GigaChatApiClient>()
        )
    }
}
