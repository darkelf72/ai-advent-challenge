package mcp_server

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class CurrentWeather(
    val time: String,
    val temperature: Double,
    val windspeed: Double,
    val winddirection: Int,
    val weathercode: Int
)

@Serializable
data class WeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val current_weather: CurrentWeather,
    val timezone: String
)

class WeatherService {
    private val logger = LoggerFactory.getLogger(WeatherService::class.java)

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun getWeather(latitude: Double, longitude: Double): String {
        return try {
            logger.info("Fetching weather for coordinates (lat: $latitude, lon: $longitude)")

            val response = httpClient.get("https://api.open-meteo.com/v1/forecast") {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("current_weather", true)
                parameter("timezone", "Europe/Moscow")
            }

            val weatherResponse: WeatherResponse = response.body()

            val result = buildString {
                appendLine("Погода по координатам ($latitude, $longitude):")
                appendLine("Температура: ${weatherResponse.current_weather.temperature}°C")
                appendLine("Скорость ветра: ${weatherResponse.current_weather.windspeed} км/ч")
                appendLine("Направление ветра: ${weatherResponse.current_weather.winddirection}°")
                appendLine("Код погоды: ${weatherResponse.current_weather.weathercode}")
                appendLine("Время измерения: ${weatherResponse.current_weather.time}")
            }

            logger.info("Successfully fetched weather for coordinates ($latitude, $longitude)")
            result

        } catch (e: Exception) {
            logger.error("Error fetching weather for coordinates ($latitude, $longitude)", e)
            "Ошибка при получении погоды: ${e.message}"
        }
    }
}
