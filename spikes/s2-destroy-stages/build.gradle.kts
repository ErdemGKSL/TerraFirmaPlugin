plugins { java }
java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("maven.modrinth:craftengine:mwFiNxVO") // 26.9.2 (API: CraftEngineBlocks.place)
    compileOnly("maven.modrinth:packetevents:m78nFxYg") // 2.14.0+spigot (sound packet log)
}
tasks.jar { archiveFileName.set("s2-destroy-stages-spike.jar") }
