plugins { java }
java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("maven.modrinth:packetevents:m78nFxYg") // 2.14.0+spigot (API + impl)
}
