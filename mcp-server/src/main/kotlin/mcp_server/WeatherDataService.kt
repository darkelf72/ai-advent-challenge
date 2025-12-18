package mcp_server

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Модель данных для записи погоды.
 */
@Serializable
data class WeatherRecord(
    val id: Int,
    val content: String,
    val createdAt: String
)

/**
 * Сервис для работы с таблицей weather.
 */
class WeatherDataService {
    private val logger = LoggerFactory.getLogger(WeatherDataService::class.java)
    private val json = Json { prettyPrint = true }

    /**
     * Сохраняет запись о погоде в базу данных.
     */
    fun saveWeatherData(content: String): Boolean {
        return try {
            DatabaseManager.getConnection().use { conn ->
                val sql = "INSERT INTO weather (content, createdAt) VALUES (?, ?)"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, content)
                    stmt.setTimestamp(2, Timestamp.from(Instant.now()))
                    val rowsAffected = stmt.executeUpdate()
                    logger.info("Weather data saved successfully. Rows affected: $rowsAffected")
                    true
                }
            }
        } catch (e: Exception) {
            logger.error("Error saving weather data to database", e)
            false
        }
    }

    /**
     * Получает записи о погоде из базы данных за указанный период.
     * @param tableName название таблицы (для будущего расширения)
     * @param startTime начало периода в ISO 8601 формате (например: "2025-12-18T10:00:00")
     * @param endTime конец периода в ISO 8601 формате
     * @return JSON строка с массивом записей
     */
    fun getWeatherData(tableName: String, startTime: String, endTime: String): String {
        return try {
            // Проверяем, что запрашивается таблица weather
            if (tableName.lowercase() != "weather") {
                logger.warn("Requested table '$tableName' is not supported. Only 'weather' table is available.")
                return json.encodeToString(emptyList<WeatherRecord>())
            }

            val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            val start = LocalDateTime.parse(startTime, formatter)
            val end = LocalDateTime.parse(endTime, formatter)

            val records = mutableListOf<WeatherRecord>()

            DatabaseManager.getConnection().use { conn ->
                val sql = """
                    SELECT id, content, createdAt
                    FROM weather
                    ORDER BY createdAt DESC
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    //todo исправь даты
//                    stmt.setTimestamp(1, Timestamp.valueOf(start))
//                    stmt.setTimestamp(2, Timestamp.valueOf(end))

                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val id = rs.getInt("id")
                        val content = rs.getString("content")
                        val createdAt = rs.getTimestamp("createdAt")
                            .toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                        records.add(WeatherRecord(id, content, createdAt))
                    }
                }
            }

            logger.info("Retrieved ${records.size} weather records from database")
            json.encodeToString(records)
//            records.joinToString { it.content }
        } catch (e: Exception) {
            logger.error("Error retrieving weather data from database", e)
            json.encodeToString(emptyList<WeatherRecord>())
        }
    }

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
