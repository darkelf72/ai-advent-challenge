import dto.ApiResponse

interface ApiClientInterface {
    fun sendRequest(query: String): ApiResponse
    fun getSystemPrompt(): String
    fun setSystemPrompt(prompt: String)
    fun clearMessages()
    fun getTemperature(): Double
    fun setTemperature(temperature: Double)
    fun getMaxTokens(): Int
    fun setMaxTokens(maxTokens: Int)
}
