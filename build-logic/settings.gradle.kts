pluginManagement {
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        gradlePluginPortal()
    }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "build-logic"
