interface ApiClientInterface {
    fun sendRequest(query: String): String
    fun getSystemPrompt(): String
    fun setSystemPrompt(prompt: String)
    fun clearMessages()
    fun getTemperature(): Double
    fun setTemperature(temperature: Double)
}
