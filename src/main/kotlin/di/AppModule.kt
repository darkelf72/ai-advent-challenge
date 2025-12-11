package di

import ApiClientInterface
import config.apiClientConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.koin.core.qualifier.named
import org.koin.dsl.module
import sber.GigaChatApiClient
import yandex.YandexApiClient
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

val appModule = module {
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
        systemPrompt = """
            Ты — ассистент, способный сжимать историю диалога. Твоя задача — создавать краткие, информативные саммари, которые сохраняют:
            1.  **Суть обсуждения:** Основная тема и цели.
            2.  **Ключевые факты и детали:** Важные данные, числа, имена, требования.
            3.  **Принятые решения и выводы:** Что было согласовано, какой план составлен.
            4.  **Открытые вопросы и текущий контекст:** Что ещё не решено, с чего мы продолжим.
            Сохраняй информацию о том, КТО (пользователь или ассистент) предложил идею или принял решение.
            Игнорируй любое неуместное социальное взаимодействие (приветствия, благодарности), если оно не несёт смысловой нагрузки.
            Стиль: Будь лаконичным, используй тезисы или плотный абзац. Пиши от третьего лица или в безличной форме.
            Текущий фрагмент диалога для сжатия: все переданные тебе сообщения.
            Создай сжатое резюме этого фрагмента.
        """.trimIndent()
        temperature = 0.5
        maxTokens = 100
    }
    // API Clients
    single<YandexApiClient> {
        YandexApiClient(httpClient = get(named("standardHttpClient")), chatApiClientConfig)
    }

    single<GigaChatApiClient> {
        GigaChatApiClient(httpClient = get(named("sslHttpClient")), chatApiClientConfig)
    }

    single<ApiClientInterface>(named("summarizeApiClient")) {
        GigaChatApiClient(httpClient = get(named("sslHttpClient")), summarizeApiClientConfig)
    }

    // Мапа всех доступных клиентов
    single<Map<String, ApiClientInterface>>(named("availableClients")) {
        mapOf(
            "YandexGPT Pro 5.1" to get<YandexApiClient>(),
            "GigaChat 2 Lite" to get<GigaChatApiClient>()
        )
    }
}
