plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "ai-advent-challenge"

include("app")
include("http-mcp-server")
include("db-mcp-server")
