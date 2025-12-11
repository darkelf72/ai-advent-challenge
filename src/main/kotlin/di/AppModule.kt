package di

import ApiClientInterface
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

    // API Clients
    single<YandexApiClient> {
        YandexApiClient(httpClient = get(named("standardHttpClient")))
    }

    single<GigaChatApiClient> {
        GigaChatApiClient(httpClient = get(named("sslHttpClient")))
    }

    // Мапа всех доступных клиентов
    single<Map<String, ApiClientInterface>>(named("availableClients")) {
        mapOf(
            "YandexGPT Pro 5.1" to get<YandexApiClient>(),
            "GigaChat 2 Lite" to get<GigaChatApiClient>()
        )
    }
}
