// PAR-WORLD-17 (spike S1): a stand-alone throwaway build, not shipped and not part of spikes/settings.gradle.kts.
// Build: spikes/gradlew -p spikes/ore-overlay jar  ->  spikes/ore-overlay/build/libs/ore-overlay-spike.jar
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
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
        mavenCentral()
    }
}
rootProject.name = "ore-overlay-spike"
