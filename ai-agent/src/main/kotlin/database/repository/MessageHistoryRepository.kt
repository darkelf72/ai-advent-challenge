package database.repository

import database.tables.MessageHistoryTable
import dto.ChatMessage
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class MessageHistoryRepository {

    fun saveMessage(clientName: String, message: ChatMessage, order: Int) {
        transaction {
            MessageHistoryTable.insert {
                it[MessageHistoryTable.clientName] = clientName
                it[role] = message.role
                it[content] = message.content
                it[messageOrder] = order
                it[createdAt] = System.currentTimeMillis()
            }
        }
    }

    fun saveMessages(clientName: String, messages: List<ChatMessage>) {
        transaction {
            // First, delete all existing messages for this client
            MessageHistoryTable.deleteWhere { MessageHistoryTable.clientName eq clientName }

            // Then insert all new messages
            messages.forEachIndexed { index, message ->
                MessageHistoryTable.insert {
                    it[MessageHistoryTable.clientName] = clientName
                    it[role] = message.role
                    it[content] = message.content
                    it[messageOrder] = index
                    it[createdAt] = System.currentTimeMillis()
                }
            }
        }
    }

    fun loadMessages(clientName: String): List<ChatMessage> {
        return transaction {
            MessageHistoryTable.selectAll()
                .where { MessageHistoryTable.clientName eq clientName }
                .orderBy(MessageHistoryTable.messageOrder to SortOrder.ASC)
                .map {
                    ChatMessage(
                        role = it[MessageHistoryTable.role],
                        content = it[MessageHistoryTable.content]
                    )
                }
        }
    }

    fun clearMessages(clientName: String) {
        transaction {
            MessageHistoryTable.deleteWhere { MessageHistoryTable.clientName eq clientName }
        }
    }

    fun getMessageCount(clientName: String): Int {
        return transaction {
            MessageHistoryTable.selectAll()
                .where { MessageHistoryTable.clientName eq clientName }
                .count()
                .toInt()
        }
    }
}
