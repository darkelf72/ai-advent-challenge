plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "ai-advent-challenge"

include("ai-agent")
include("http-mcp-server")
include("db-mcp-server")
include("local-mcp-server")
include("git-mcp-server")
include("github-mcp-server")
