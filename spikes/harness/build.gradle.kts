import xyz.jpenilla.runpaper.task.RunServer

plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

dependencies {
    // Lowest supported API (M0-JAR-01 checks the same jar on 26.x).
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

val java25 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }

// Pinned Modrinth version ids (looked up 2026-10-16).
val craftEngine = "mwFiNxVO"   // 26.9.2 (paper/folia)
val betterModel = "RZkXlsnV"   // 3.5.1 (paper/folia)
val betterHud = "VHVKage9"     // 2.2.0-SNAPSHOT-449
val packetEvents = "m78nFxYg"  // 2.14.0+spigot

/** The four M0 targets: platform x MC version. */
val targets = listOf("paper" to "1.21.11", "paper" to "26.3", "folia" to "1.21.11", "folia" to "26.3")

for ((platform, mc) in targets) {
    val name = "run" + platform.replaceFirstChar(Char::uppercase) + mc.replace(".", "_")
    tasks.register<RunServer>(name) {
        group = "spike"
        description = "Run $platform $mc with CE/BM/BH/PacketEvents and the spike plugins"
        if (platform == "folia") {
            downloadsApiService.set(xyz.jpenilla.runtask.service.DownloadsAPIService.folia(project))
        }
        minecraftVersion(mc)
        javaLauncher = java25
        val runName = providers.gradleProperty("runName").orNull
        runDirectory = rootProject.layout.projectDirectory.dir(if (runName == null) "run/$platform-$mc" else "run/$runName/$platform-$mc")
        pluginJars.from(tasks.jar)
        // Extra spike plugins (e.g. -PspikeJars=../ce-01/build/libs/x.jar) can be added per run.
        providers.gradleProperty("spikeJars").orNull?.split(",")?.forEach { pluginJars.from(rootProject.file(it)) }
        downloadPlugins {
            modrinth("craftengine", craftEngine)
            modrinth("bettermodel", betterModel)
            modrinth("betterhud2", betterHud)
            modrinth("packetevents", packetEvents)
        }
        jvmArgs(
            "-Xmx3G",
            "-Dcom.mojang.eula.agree=true",
            "-Dtfp.assertThreads=true",
            "-Dpaper.disableChannelLimit=true",
        )
        providers.gradleProperty("autostop").orNull?.let { jvmArgs("-Dtfp.spike.autostop=$it") }
        args("--nogui")
    }
}
