plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    // Ktor Client
    implementation("io.ktor:ktor-client-core:3.0.2")
    implementation("io.ktor:ktor-client-cio:3.0.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")

    // Ktor Server
    implementation("io.ktor:ktor-server-core:3.0.2")
    implementation("io.ktor:ktor-server-netty:3.0.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-server-html-builder:3.0.2")
    implementation("io.ktor:ktor-server-cors:3.0.2")

    // Koin for Dependency Injection
    implementation("io.insert-koin:koin-core:4.0.0")
    implementation("io.insert-koin:koin-ktor:4.0.0")

    // Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.57.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.57.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.57.0")

    // SQLite JDBC Driver
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // Flyway for Database Migrations
    implementation("org.flywaydb:flyway-core:11.1.0")

    // SLF4J for Logging
    implementation("org.slf4j:slf4j-simple:2.0.16")

    //
    implementation("io.modelcontextprotocol:kotlin-sdk:0.8.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}

application {
    mainClass.set("MainKt")
}

tasks.named<JavaExec>("run") {
    systemProperty("yandexApiKey", project.findProperty("yandexApiKey") as String? ?: "")
    systemProperty("gigaChatApiKey", project.findProperty("gigaChatApiKey") as String? ?: "")
}
