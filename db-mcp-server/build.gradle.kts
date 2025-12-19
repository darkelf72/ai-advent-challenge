plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    // Ktor Client
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")

    // Ktor Server
    implementation("io.ktor:ktor-server-core:3.0.2")
    implementation("io.ktor:ktor-server-netty:3.0.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-server-html-builder:3.0.2")
    implementation("io.ktor:ktor-server-cors:3.0.2")
    implementation("io.ktor:ktor-server-sse-jvm:3.0.2")

    // Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.57.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.57.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.57.0")

    //
    implementation("io.modelcontextprotocol:kotlin-sdk:0.8.1")
    //
    implementation("ch.qos.logback:logback-classic:1.5.20")

    // SQLite JDBC Driver
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // Flyway for Database Migrations
    implementation("org.flywaydb:flyway-core:11.1.0")

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

tasks.named<JavaExec>("run") {
    // Устанавливаем рабочую директорию в директорию модуля mcp-server
    workingDir = projectDir
}