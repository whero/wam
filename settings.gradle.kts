plugins {
    // Auto-provision the Java 21 toolchain when no matching JDK is installed,
    // so the build works on machines running any other Java version (e.g. 25)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "whero-another-maintenance"
