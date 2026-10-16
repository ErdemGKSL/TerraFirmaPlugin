// core/ and worldgen/: platform-free. The build fails if Bukkit/Paper/NMS or a client plugin API
// shows up on their classpath, directly or transitively (ARCHITECTURE.md §3).
plugins { id("tfp.java-conventions") }

val forbiddenGroups = listOf(
    "io.papermc", "org.bukkit", "org.spigotmc", "com.destroystokyo", "net.md-5",
    "maven.modrinth", "net.momirealms", "io.github.toxicity188", "com.github.retrooper",
)

val verifyPlatformFree = tasks.register("verifyPlatformFree") {
    group = "verification"
    description = "Fails if a platform/plugin dependency is on this pure module's classpath."
    val classpaths = listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath")
        .map { configurations.named(it) }
    val ids = classpaths.map { cp -> cp.flatMap { c -> c.incoming.resolutionResult.rootComponent.map { root ->
        val out = mutableListOf<String>()
        fun walk(r: org.gradle.api.artifacts.result.ResolvedComponentResult, seen: MutableSet<Any>) {
            for (d in r.dependencies) if (d is org.gradle.api.artifacts.result.ResolvedDependencyResult && seen.add(d.selected.id)) {
                out += d.selected.id.displayName
                walk(d.selected, seen)
            }
        }
        walk(root, mutableSetOf())
        out
    } } }
    inputs.property("forbidden", forbiddenGroups)
    doLast {
        val bad = ids.flatMap { it.get() }.distinct().filter { id -> forbiddenGroups.any { id.startsWith("$it:") || id.startsWith("$it.") } }
        check(bad.isEmpty()) { "Pure module ${project.path} must not depend on platform APIs: $bad" }
    }
}

tasks.named("check") { dependsOn(verifyPlatformFree) }
