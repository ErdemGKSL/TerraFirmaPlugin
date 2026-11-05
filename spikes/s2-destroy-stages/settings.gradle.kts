// PAR-INFRA-17 (spike S2): a stand-alone throwaway build, not shipped and not part of spikes/settings.gradle.kts.
// Build: spikes/gradlew -p spikes/s2-destroy-stages jar  ->  spikes/s2-destroy-stages/build/libs/s2-destroy-stages-spike.jar
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
rootProject.name = "s2-destroy-stages-spike"
