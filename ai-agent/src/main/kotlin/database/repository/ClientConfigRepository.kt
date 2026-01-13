package database.repository

import apiclients.config.ApiClientConfig
import database.tables.ClientConfigTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class ClientConfigRepository {

    fun saveConfig(clientName: String, config: ApiClientConfig) {
        transaction {
            val existingConfig = ClientConfigTable.selectAll()
                .where { ClientConfigTable.clientName eq clientName }
                .singleOrNull()

            if (existingConfig != null) {
                ClientConfigTable.update({ ClientConfigTable.clientName eq clientName }) {
                    it[systemPrompt] = config.systemPrompt
                    it[temperature] = config.temperature
                    it[maxTokens] = config.maxTokens
                    it[autoSummarizeThreshold] = config.autoSummarizeThreshold
                }
            } else {
                ClientConfigTable.insert {
                    it[ClientConfigTable.clientName] = clientName
                    it[systemPrompt] = config.systemPrompt
                    it[temperature] = config.temperature
                    it[maxTokens] = config.maxTokens
                    it[autoSummarizeThreshold] = config.autoSummarizeThreshold
                }
            }
        }
    }

    fun loadConfig(clientName: String): ApiClientConfig? {
        return transaction {
            ClientConfigTable.selectAll()
                .where { ClientConfigTable.clientName eq clientName }
                .singleOrNull()
                ?.let {
                    ApiClientConfig(
                        systemPrompt = it[ClientConfigTable.systemPrompt],
                        temperature = it[ClientConfigTable.temperature],
                        maxTokens = it[ClientConfigTable.maxTokens],
                        autoSummarizeThreshold = it[ClientConfigTable.autoSummarizeThreshold]
                    )
                }
        }
    }

    fun deleteConfig(clientName: String) {
        transaction {
            ClientConfigTable.deleteWhere { ClientConfigTable.clientName eq clientName }
        }
    }
}
