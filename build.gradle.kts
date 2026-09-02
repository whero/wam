plugins {
    kotlin("jvm") version "2.4.10"
    // Bundles the Kotlin stdlib into the plugin jar (Paper does not provide it at runtime)
    id("com.gradleup.shadow") version "9.6.0"
}

group = "net.whero"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.62-beta")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
}

tasks.jar {
    // The shadow jar replaces the plain jar as the published artifact
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("WheroAnotherMaintenance")
    archiveClassifier.set("")
    // Strip unused stdlib classes so the jar stays small
    minimize()
    // Kotlin module metadata is compile-time only and cannot be merged across jars
    exclude("META-INF/*.kotlin_module")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(
            "version" to project.version
        )
    }
}
