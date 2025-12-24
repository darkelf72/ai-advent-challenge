plugins {
    kotlin("jvm") version "2.2.20" apply false
    kotlin("plugin.serialization") version "2.2.20" apply false
}

allprojects {
    group = "org.example"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    // Load .env file for all JavaExec tasks in all modules
    tasks.withType<JavaExec>().configureEach {
        val envFile = rootProject.file(".env")
        if (envFile.exists()) {
            envFile.readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
                .forEach { line ->
                    val (key, value) = line.split("=", limit = 2)
                    environment(key.trim(), value.trim())
                }
        }
    }
}
