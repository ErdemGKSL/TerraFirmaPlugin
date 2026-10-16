// The server side: bootstrap, schedulers, listeners, stores, GUIs, commands, generator, packet layer.
// Talks to CraftEngine/BetterModel/BetterHud only through interfaces implemented in :integration:*.
plugins { id("tfp.paper-conventions") }
dependencies {
    api(project(":api"))
    api(project(":core"))
    api(project(":worldgen"))
    testImplementation(libs.archunit)
}
tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}
