package mcp

import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation

class FooMcpClient(httpClient: HttpClient) {
    private val mcpClient = Client(
        clientInfo = Implementation(
            name = "mcp-cli-client",
            version = "1.0.0"
        ),
        options = ClientOptions()
    )


}