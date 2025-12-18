package controllers

import apiclients.ApiClientInterface
import dto.request.ClientSwitchRequest
import dto.response.ClientSwitchResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/**
 * Controller for client switching operations
 */
class ClientController(
    private val availableClients: Map<String, ApiClientInterface>
) {
    var currentClientName: String = "GigaChat 2 Lite"
        private set

    var apiClient: ApiClientInterface = availableClients.getValue(currentClientName)
        private set

    suspend fun handleGetCurrentClient(call: ApplicationCall) {
        call.respond(mapOf("clientName" to currentClientName))
    }

    suspend fun handleGetAvailableClients(call: ApplicationCall) {
        call.respond(mapOf("clients" to availableClients.keys.toList()))
    }

    suspend fun handleSwitchClient(call: ApplicationCall) {
        val request = call.receive<ClientSwitchRequest>()
        val newClient = availableClients[request.clientName]

        if (newClient != null) {
            apiClient = newClient
            currentClientName = request.clientName
            call.respond(
                ClientSwitchResponse(
                    clientName = currentClientName,
                    systemPrompt = apiClient.config.systemPrompt,
                    temperature = apiClient.config.temperature
                )
            )
        } else {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown client: ${request.clientName}"))
        }
    }
}
