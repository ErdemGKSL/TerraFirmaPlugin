// Throwaway spike build (plans/00-m0-spikes.md). Nothing here is production code.
pluginManagement {
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositories {
        // repo.papermc.io proxies Maven Central, which rate-limits CI/containers.
        maven("https://repo.papermc.io/repository/maven-public/")
        // Modrinth's Maven endpoint: maven.modrinth:<slug>:<version id>. CE's own repo
        // (repo.momirealms.net) is not always reachable, the Modrinth jar carries the API.
        maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
        mavenCentral()
    }
}
rootProject.name = "tfp-spikes"
include("harness", "ce-01", "vis-01")
