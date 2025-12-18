# MCP Server

A standalone Model Context Protocol (MCP) server implementation using Kotlin, Ktor, and the official MCP Kotlin SDK.

## Overview

This module implements an MCP server that exposes tools via Server-Sent Events (SSE) over HTTP. It demonstrates the MCP
protocol with a simple string reversal tool.

## Features

- **MCP Protocol Support**: Implements the Model Context Protocol specification
- **SSE Transport**: Uses Server-Sent Events for real-time communication
- **Simple Tool**: Provides a "reverse" tool that reverses input strings
- **Standalone Server**: Runs independently on port 8082

## Architecture

- **Framework**: Ktor with Netty engine
- **SDK**: io.modelcontextprotocol:kotlin-sdk v0.8.1
- **Transport**: Server-Sent Events (SSE)
- **Ports**:
    - HTTP: 8082 (default)
    - HTTPS: 8443 (when SSL enabled)
- **SSL/TLS**: Auto-generated or custom certificates

## Available Tools

### reverse

Reverses the input string.

**Parameters:**

- `text` (string, required): The text to reverse

**Example:**

```json
{
  "text": "Hello, World!"
}
```

**Response:**

```json
{
  "content": [
    {
      "type": "text",
      "text": "!dlroW ,olleH"
    }
  ]
}
```

## Running the Server

### Build the Module

```bash
.\gradlew.bat :mcp-server:build
```

### Run the Server

```bash
.\gradlew.bat :mcp-server:run
```

The server will start on `http://localhost:8082`

### Health Check

```bash
curl http://localhost:8082
```

Response: `MCP Server is running on port 8082`

## HTTPS/SSL Support

The MCP server supports HTTPS for secure connections. HTTPS is automatically enabled when:

1. A keystore file exists at `mcp-server/src/main/resources/keystore.jks`, OR
2. The environment variable `SSL_ENABLED=true` is set

### Quick Start with Auto-Generated Certificate

If no keystore exists, the server will automatically generate a temporary self-signed certificate on startup for
development purposes.

### Creating a Persistent Keystore

For production or persistent development, generate a keystore file:

```bash
# Navigate to the mcp-server resources directory
cd mcp-server\src\main\resources

# Generate a self-signed certificate (valid for 10 years)
keytool -genkeypair -alias mcpserver -keyalg RSA -keysize 2048 \
  -storetype JKS -keystore keystore.jks -validity 3650 \
  -storepass changeit -keypass changeit \
  -dname "CN=localhost, OU=MCP, O=MCP Server, L=Unknown, ST=Unknown, C=US"
```

**Windows:**

```cmd
cd mcp-server\src\main\resources
keytool -genkeypair -alias mcpserver -keyalg RSA -keysize 2048 -storetype JKS -keystore keystore.jks -validity 3650 -storepass changeit -keypass changeit -dname "CN=localhost, OU=MCP, O=MCP Server, L=Unknown, ST=Unknown, C=US"
```

### SSL Configuration

The server runs on two ports when SSL is enabled:

- **HTTP**: Port 8082 (default)
- **HTTPS**: Port 8443 (default)

**Environment Variables:**

- `SSL_ENABLED`: Enable SSL even without keystore file (generates temp certificate)
- `SSL_KEY_ALIAS`: Keystore alias (default: `mcpserver`)
- `SSL_KEYSTORE_PASSWORD`: Keystore password (default: `changeit`)
- `SSL_KEY_PASSWORD`: Private key password (default: `changeit`)

**Example:**

```bash
# Run with custom SSL settings
set SSL_ENABLED=true
set SSL_KEYSTORE_PASSWORD=mypassword
set SSL_KEY_PASSWORD=mypassword
.\gradlew.bat :mcp-server:run
```

### Accessing HTTPS Endpoint

Once SSL is enabled:

```bash
# HTTPS health check (may show certificate warning for self-signed certs)
curl -k https://localhost:8443

# HTTP still available
curl http://localhost:8082
```

### Production Considerations

For production deployments:

1. Use a properly signed certificate from a trusted Certificate Authority (CA)
2. Update the keystore with your production certificate
3. Use strong, unique passwords (not the defaults)
4. Consider using environment variables for sensitive credentials
5. Disable HTTP if only HTTPS is needed

## MCP Protocol Endpoints

The MCP server automatically provides the following capabilities:

- **Initialization**: MCP handshake and capability exchange
- **Tools List**: Discovery of available tools (`tools/list`)
- **Tool Execution**: Calling tools with arguments (`tools/call`)

## Using with MCP Clients

### With Claude Code

You can use this MCP server with Claude Code or any other MCP-compatible client by configuring it to connect to
`http://localhost:8082/sse` (or the appropriate MCP endpoint).

### With Custom Clients

Use the MCP Kotlin SDK client to connect:

```kotlin
val client = Client(
    clientInfo = Implementation(name = "my-client", version = "1.0.0"),
    options = ClientOptions()
)

val transport = SseClientTransport("http://localhost:8082/sse")
client.connect(transport)

// List available tools
val tools = client.listTools()

// Call the reverse tool
val result = client.callTool("reverse", mapOf("text" to "Hello"))
```

## Configuration

- **Port**: Configured in `application.conf` (default: 8082)
- **Logging**: Configured in `logback.xml`

## Dependencies

- Kotlin 2.2.21
- Ktor 3.3.3
- MCP Kotlin SDK 0.8.1

## Project Structure

```
mcp-server/
├── build.gradle.kts
├── README.md
└── src/
    └── main/
        ├── kotlin/ru/sber/cb/aichallenge_one/mcp_server/
        │   ├── Application.kt          # Main entry point
        │   └── McpConfiguration.kt     # MCP server setup
        └── resources/
            ├── application.conf         # Server configuration
            └── logback.xml              # Logging configuration
```

## Development

### Adding New Tools

To add a new tool, edit `McpConfiguration.kt`:

```kotlin
addTool(
    Tool(
        name = "your-tool-name",
        description = "Tool description",
        inputSchema = ObjectSchema(
            properties = mapOf(
                "param1" to StringSchema(description = "Parameter description")
            ),
            required = listOf("param1")
        )
    )
) { arguments ->
    val param1 = (arguments["param1"] as? String) ?: ""
    // Your tool logic here
    CallToolResult(
        content = listOf(TextContent(text = "Result"))
    )
}
```

## References

- [MCP Kotlin SDK Documentation](https://modelcontextprotocol.github.io/kotlin-sdk/)
- [MCP Protocol Specification](https://modelcontextprotocol.io/)
- [Ktor Documentation](https://ktor.io/)
