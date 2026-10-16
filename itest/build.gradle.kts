import xyz.jpenilla.runpaper.task.RunServer
import xyz.jpenilla.runtask.service.DownloadsAPIService

// In-server self-test plugin (M1-TEST-01). Runs under run-paper in CI (M1-BUILD-03):
//   ./gradlew :itest:runSelfTest -Pmc=26.3 -Pplatform=folia
// The self-test plugin writes selftest-report.xml (JUnit format) into the run folder and stops the server;
// runSelfTest then fails unless that report exists and has no failures.
plugins {
    id("tfp.paper-conventions")
    id("xyz.jpenilla.run-paper")
}
dependencies { compileOnly(project(":paper")) }

val mc = providers.gradleProperty("mc").orElse("26.3")
val platform = providers.gradleProperty("platform").orElse("paper")
val java25 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
val runDir = layout.buildDirectory.dir(mc.zip(platform) { v, p -> "selftest-run/$p-$v" })
val pluginJar = project(":dist").tasks.named<Jar>("shadowJar")
// The self-test plugin descriptor; absent until M1-TEST-01 lands.
val selfTestDescriptor = layout.projectDirectory.file("src/main/resources/paper-plugin.yml")

val runSelfTest = tasks.register<RunServer>("runSelfTest") {
    group = "verification"
    description = "Runs the in-server self-test on -Pplatform=paper|folia and -Pmc=<version> with CE/BM/BH installed."
    val p = platform.get()
    require(p == "paper" || p == "folia") { "-Pplatform must be paper or folia, got $p" }
    if (p == "folia") {
        downloadsApiService.set(DownloadsAPIService.folia(project))
    }
    minecraftVersion(mc.get())
    javaLauncher = java25
    runDirectory = runDir
    pluginJars.from(pluginJar.flatMap { it.archiveFile })
    pluginJars.from(tasks.jar.flatMap { it.archiveFile })
    // Pinned Modrinth version ids from gradle/libs.versions.toml (one place for build and CI).
    downloadPlugins {
        modrinth("craftengine", libs.versions.craftengine.get())
        modrinth("bettermodel", libs.versions.bettermodel.get())
        modrinth("betterhud2", libs.versions.betterhud.get())
        modrinth("packetevents", libs.versions.packetevents.get())
    }
    jvmArgs(
        "-Xmx3G",
        "-Dcom.mojang.eula.agree=true",
        "-Dtfp.selftest=true",
        "-Dtfp.assertThreads=true",
        "-Dpaper.disableChannelLimit=true",
    )
    args("--nogui")
    val descriptor = selfTestDescriptor.asFile
    val report = runDir.map { it.file("selftest-report.xml").asFile }
    doFirst {
        check(descriptor.exists()) {
            "The self-test plugin does not exist yet (M1-TEST-01): ${descriptor.relativeTo(rootDir)} is missing. " +
                "Refusing to start a server that would never stop."
        }
        report.get().delete()
    }
    doLast {
        val r = report.get()
        check(r.exists()) { "No self-test report at $r: the server stopped before the self-test finished" }
        val xml = r.readText()
        val failures = Regex("""(failures|errors)="(\d+)"""").findAll(xml).sumOf { it.groupValues[2].toInt() }
        check(failures == 0) { "Self-test failed ($failures failing case(s)); see $r and the server log" }
    }
}
