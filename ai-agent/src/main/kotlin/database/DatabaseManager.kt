package database

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource
import org.sqlite.SQLiteDataSource

object DatabaseManager {
    private const val DB_URL = "jdbc:sqlite:./chat_data.db"
    private lateinit var dataSource: DataSource

    fun init() {
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

    fun getDataSource(): DataSource = dataSource
}
