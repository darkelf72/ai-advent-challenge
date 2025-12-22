package database

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteDataSource
import javax.sql.DataSource

/**
 * Database manager for embeddings storage
 * Uses separate SQLite database (embeddings.db) to allow easy migration to vector database
 */
object EmbeddingDatabaseManager {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private const val DB_URL = "jdbc:sqlite:./embeddings.db"
    private lateinit var dataSource: DataSource
    private lateinit var database: Database

    fun init() {
        try {
            logger.info("Initializing embeddings database...")

            // Create SQLite DataSource
            dataSource = SQLiteDataSource().apply {
                url = DB_URL
            }

            // Run Flyway migrations
            runMigrations()

            // Connect Exposed to the database
            database = Database.connect(dataSource)

            logger.info("Embeddings database initialized successfully at: $DB_URL")
        } catch (e: Exception) {
            logger.error("Failed to initialize embeddings database", e)
            throw e
        }
    }

    private fun runMigrations() {
        try {
            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/embeddings_migration")
                .load()

            val migrationsApplied = flyway.migrate()
            logger.info("Flyway migrations applied for embeddings database: ${migrationsApplied.migrationsExecuted}")
        } catch (e: Exception) {
            logger.error("Failed to run migrations for embeddings database", e)
            throw e
        }
    }

    fun getDataSource(): DataSource = dataSource

    fun getDatabase(): Database = database
}
