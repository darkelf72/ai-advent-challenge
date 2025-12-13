package database.tables

import org.jetbrains.exposed.sql.Table

object ClientConfigTable : Table("client_config") {
    val id = integer("id").autoIncrement()
    val clientName = varchar("client_name", 50).uniqueIndex()
    val systemPrompt = text("system_prompt")
    val temperature = double("temperature")
    val maxTokens = integer("max_tokens")

    override val primaryKey = PrimaryKey(id)
}
