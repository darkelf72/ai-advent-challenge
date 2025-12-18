package controllers

import dto.request.AutoSummarizeThresholdRequest
import dto.request.MaxTokensRequest
import dto.request.SystemPromptRequest
import dto.request.TemperatureRequest
import dto.response.AutoSummarizeThresholdResponse
import dto.response.MaxTokensResponse
import dto.response.SystemPromptResponse
import dto.response.TemperatureResponse
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import service.SummarizationService

/**
 * Controller for configuration-related operations
 */
class ConfigController(
    private val clientController: ClientController,
    private val summarizationService: SummarizationService
) {

    suspend fun handleGetSystemPrompt(call: ApplicationCall) {
        call.respond(SystemPromptResponse(prompt = clientController.apiClient.config.systemPrompt))
    }

    suspend fun handleSetSystemPrompt(call: ApplicationCall) {
        val request = call.receive<SystemPromptRequest>()
        clientController.apiClient.config = clientController.apiClient.config.copy(systemPrompt = request.prompt)
        call.respond(SystemPromptResponse(prompt = request.prompt))
    }

    suspend fun handleGetTemperature(call: ApplicationCall) {
        call.respond(TemperatureResponse(temperature = clientController.apiClient.config.temperature))
    }

    suspend fun handleSetTemperature(call: ApplicationCall) {
        val request = call.receive<TemperatureRequest>()
        clientController.apiClient.config = clientController.apiClient.config.copy(temperature = request.temperature)
        call.respond(TemperatureResponse(temperature = request.temperature))
    }

    suspend fun handleGetMaxTokens(call: ApplicationCall) {
        call.respond(MaxTokensResponse(maxTokens = clientController.apiClient.config.maxTokens))
    }

    suspend fun handleSetMaxTokens(call: ApplicationCall) {
        val request = call.receive<MaxTokensRequest>()
        clientController.apiClient.config = clientController.apiClient.config.copy(maxTokens = request.maxTokens)
        call.respond(MaxTokensResponse(maxTokens = request.maxTokens))
    }

    suspend fun handleGetAutoSummarizeThreshold(call: ApplicationCall) {
        call.respond(AutoSummarizeThresholdResponse(threshold = clientController.apiClient.config.autoSummarizeThreshold))
    }

    suspend fun handleSetAutoSummarizeThreshold(call: ApplicationCall) {
        val request = call.receive<AutoSummarizeThresholdRequest>()
        clientController.apiClient.config = clientController.apiClient.config.copy(autoSummarizeThreshold = request.threshold)
        call.respond(AutoSummarizeThresholdResponse(threshold = request.threshold))
    }

    suspend fun handleSummarize(call: ApplicationCall) {
        try {
            val result = summarizationService.summarize(clientController.apiClient)
            call.respond(result)
        } catch (e: Exception) {
            println("Auto-summarization failed: ${e.message}")
        }
    }
}
