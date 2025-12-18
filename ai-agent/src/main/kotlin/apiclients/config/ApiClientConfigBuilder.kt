package apiclients.config

class ApiClientConfigBuilder {
    var systemPrompt: String = "Ты - генеративная языковая модель"
    var temperature: Double = 0.7
    var maxTokens: Int = 100

    fun build(): ApiClientConfig =
        ApiClientConfig(
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = maxTokens,
        )
}

fun apiClientConfig(block: ApiClientConfigBuilder.() -> Unit): ApiClientConfig =
    ApiClientConfigBuilder().apply(block).build()