plugins { java }
java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("maven.modrinth:betterhud2:VHVKage9") // 2.2.0-SNAPSHOT-449 bukkit jar (contains the API)
    compileOnly("maven.modrinth:craftengine:mwFiNxVO")
}
