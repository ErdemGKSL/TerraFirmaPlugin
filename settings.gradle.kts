pluginManagement {
    includeBuild("build-logic")
    repositories {
        // repo.papermc.io proxies Maven Central, which rate-limits unauthenticated CI/containers.
        maven("https://repo.papermc.io/repository/maven-public/")
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        // Modrinth Maven: CraftEngine/BetterModel/BetterHud/PacketEvents plugin jars (they contain their APIs).
        maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
        mavenCentral()
        // NeoForge universal jar (datagen reads its data/c/tags, PAR-INFRA-14); only that module comes from here.
        exclusiveContent {
            forRepository { maven("https://maven.neoforged.net/releases") }
            filter { includeModule("net.neoforged", "neoforge") }
        }
    }
}

rootProject.name = "terrafirmaplugin"

include(
    "api",
    "core",
    "worldgen",
    "paper",
    "integration:craftengine",
    "integration:bettermodel",
    "integration:betterhud",
    "content",
    "tools:datagen",
    "itest",
    "dist",
)
