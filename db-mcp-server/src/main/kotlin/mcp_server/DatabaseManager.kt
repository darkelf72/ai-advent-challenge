package mcp_server

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource
import org.sqlite.SQLiteDataSource
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

object DatabaseManager {
    // Определяем путь к БД в текущей рабочей директории
    private val DB_PATH = File(System.getProperty("user.dir"), "mcp_data.db").absolutePath
    private val DB_URL = "jdbc:sqlite:$DB_PATH"
    private lateinit var dataSource: DataSource

    fun init() {
//        // Проверяем существование БД
//        val dbFile = File(DB_PATH)
//        val dbExists = dbFile.exists()
//
//        if (!dbExists) {
//            println("Database file not found at: $DB_PATH")
//            println("Creating new database...")
//
//            // Создаем файл БД, установив соединение
//            DriverManager.getConnection(DB_URL).use { connection ->
//                println("Database file created successfully")
//            }
//        } else {
//            println("Database file found at: $DB_PATH")
//        }

        // Create SQLite DataSource
        dataSource = SQLiteDataSource().apply {
            url = DB_URL
        }

        // Run Flyway migrations
        runMigrations()

        // Connect Exposed to the database
        Database.connect(dataSource)

        println("Database initialized successfully at: $DB_URL")
    }

    private fun runMigrations() {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()

        val migrationsApplied = flyway.migrate()
        println("Flyway migrations applied: ${migrationsApplied.migrationsExecuted}")
    }

    fun getConnection(): Connection {
        return DriverManager.getConnection(DB_URL)
    }
}

