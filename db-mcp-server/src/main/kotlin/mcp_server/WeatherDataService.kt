package mcp_server

import org.slf4j.LoggerFactory

/**
 * Сервис для работы с таблицей weather.
 */
class WeatherDataService {
    private val logger = LoggerFactory.getLogger(WeatherDataService::class.java)

    /**
     * Сохраняет детализированную информацию о погоде в таблицу weather_in_city.
     * @param dateTime дата и время в ISO 8601 формате
     * @param cityName название города
     * @param temperature температура
     * @param windSpeed скорость ветра
     * @param windDirection направление ветра
     * @return ID созданной записи или null в случае ошибки
     */
    fun saveWeatherInCity(
        dateTime: String,
        cityName: String,
        temperature: Double,
        windSpeed: Double,
        windDirection: Int
    ): Int? {
        return try {
            DatabaseManager.getConnection().use { conn ->
                val sql = """
                    INSERT INTO weather_in_city (date_time, city_name, temperature, wind_speed, wind_direction)
                    VALUES (?, ?, ?, ?, ?)
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, dateTime)
                    stmt.setString(2, cityName)
                    stmt.setDouble(3, temperature)
                    stmt.setDouble(4, windSpeed)
                    stmt.setInt(5, windDirection)

                    val rowsAffected = stmt.executeUpdate()
                    logger.info("Weather in city data saved successfully. Rows affected: $rowsAffected")

                    // Получаем ID последней вставленной записи
                    val generatedKeys = stmt.generatedKeys
                    if (generatedKeys.next()) {
                        val id = generatedKeys.getInt(1)
                        logger.info("Generated ID: $id")
                        id
                    } else {
                        logger.error("Failed to retrieve generated ID")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error saving weather in city data to database", e)
            null
        }
    }
}
