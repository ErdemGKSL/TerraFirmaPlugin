plugins { java }
java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // CraftEngine 26.9.2 paper jar from Modrinth (contains core + bukkit API, relocated libs).
    compileOnly("maven.modrinth:craftengine:mwFiNxVO")
}
