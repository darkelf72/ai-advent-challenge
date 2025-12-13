package database.tables

import org.jetbrains.exposed.sql.Table

object MessageHistoryTable : Table("message_history") {
    val id = integer("id").autoIncrement()
    val clientName = varchar("client_name", 50)
    val role = varchar("role", 20)
    val content = text("content")
    val messageOrder = integer("message_order")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
