plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:3.0.2")
    implementation("io.ktor:ktor-server-netty:3.0.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-server-html-builder:3.0.2")
    implementation("io.ktor:ktor-server-cors:3.0.2")
    implementation("io.ktor:ktor-server-sse-jvm:3.0.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")

    // MCP SDK
    implementation("io.modelcontextprotocol:kotlin-sdk:0.8.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.20")

    // JGit for Git operations
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("mcp_server.ApplicationKt")
}
