// The server side: bootstrap, schedulers, listeners, stores, GUIs, commands, generator, packet layer.
// Talks to CraftEngine/BetterModel/BetterHud only through interfaces implemented in :integration:*.
plugins { id("tfp.paper-conventions") }
dependencies {
    api(project(":api"))
    api(project(":core"))
    api(project(":worldgen"))
    // M1-SVC-03: the packet layer (dev.tfp.paper.packet only) links against the PacketEvents plugin (M0-NET-01).
    compileOnly(libs.packetevents)
    testImplementation(libs.archunit)
}
tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
    // M1-BOOT-02: the compatibility table lives with the other content sources; shipped at the jar root.
    from(rootProject.layout.projectDirectory.file("content/compat.yml"))
}
// M1-SVC-01 (RegistryService test) and M1-REG: the datagen output, read like the jar's /content/data.
val contentData = rootProject.layout.projectDirectory.dir("content/generated/content/data")
tasks.test {
    systemProperty("tfp.contentData", contentData.asFile.absolutePath)
    inputs.files(rootProject.fileTree(contentData))
        .withPropertyName("contentData")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
