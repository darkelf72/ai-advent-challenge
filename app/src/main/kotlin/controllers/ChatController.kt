package controllers

import dto.request.ChatRequest
import dto.response.ChatResponse
import dto.response.MessageHistoryResponse
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/**
 * Controller for chat-related operations
 */
class ChatController(private val clientController: ClientController) {

    suspend fun handleSendMessage(call: ApplicationCall) {
        val request = call.receive<ChatRequest>()
        val apiResponse = clientController.apiClient.sendRequest(request.message)

        val foo = """
            🕒 ${apiResponse.result!!.elapsedTime} мс
            ⬆️ ${apiResponse.result.promptTokens} токенов
            ⬇️ ${apiResponse.result.completionTokens} токенов
            ↕️ ${apiResponse.result.totalTokens} токенов
            💸 ${apiResponse.result.cost} руб
        """.trimIndent()

        val chatResponse = ChatResponse(
            question = request.message,
            answer = apiResponse.message,
            result = foo
        )
        call.respond(chatResponse)
    }

    suspend fun handleGetMessageHistory(call: ApplicationCall) {
        val history = clientController.apiClient.messageHistory.map { message ->
            mapOf("role" to message.role, "content" to message.content)
        }
        call.respond(MessageHistoryResponse(messages = history))
    }

    suspend fun handleClearHistory(call: ApplicationCall) {
        clientController.apiClient.clearMessages()
        call.respond(mapOf("status" to "ok"))
    }
}
