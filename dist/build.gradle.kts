import java.util.zip.ZipFile

// The single plugin jar: paper + integrations + core/worldgen/api + content.
plugins { id("tfp.dist-conventions") }
dependencies {
    implementation(project(":paper"))
    implementation(project(":integration:craftengine"))
    implementation(project(":integration:bettermodel"))
    implementation(project(":integration:betterhud"))
    implementation(project(":content"))
}
tasks.shadowJar {
    archiveBaseName = "TerraFirmaPlugin"
    archiveClassifier = ""
    mergeServiceFiles()
    // Shadow 9 drops duplicate paths before transformers see them; service files must reach the merger
    // (the integration adapters each ship META-INF/services/dev.tfp.paper.integration.IntegrationProvider).
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    filesNotMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
    // Libraries we bundle are relocated so they never clash with other plugins' copies.
    relocate("com.github.benmanes.caffeine", "dev.tfp.lib.caffeine")
    relocate("org.jctools", "dev.tfp.lib.jctools")
    // Provided by the server: never bundle.
    dependencies {
        exclude(dependency("com.google.code.gson:gson"))
        exclude(dependency("org.jspecify:jspecify"))
        exclude(dependency("com.google.errorprone:error_prone_annotations"))
    }
}
tasks.jar { enabled = false }
tasks.assemble { dependsOn(tasks.shadowJar) }

// Architecture rules over every module's classes (they are all on this classpath).
dependencies {
    testImplementation(libs.archunit)
    testImplementation(libs.paper.api)
}

// M1-INT-01: TfpPlugin finds the adapters with ServiceLoader, so the shaded jar's merged service file
// must name all three providers (Shadow silently kept only the first copy before the INCLUDE above).
val verifyIntegrationServices = tasks.register("verifyIntegrationServices") {
    group = "verification"
    val jar = tasks.shadowJar.flatMap { it.archiveFile }
    inputs.file(jar)
    doLast {
        val path = "META-INF/services/dev.tfp.paper.integration.IntegrationProvider"
        val providers = ZipFile(jar.get().asFile).use { z ->
            val entry = z.getEntry(path) ?: error("$path missing from the plugin jar")
            z.getInputStream(entry).bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }
        val expected = listOf("craftengine.CraftEngineProvider", "bettermodel.BetterModelProvider", "betterhud.BetterHudProvider")
            .map { "dev.tfp.integration.$it" }
        check(providers.containsAll(expected)) { "$path lists $providers, expected $expected" }
    }
}
tasks.named("check") { dependsOn(verifyIntegrationServices) }

// PAR-INFRA-24: the shaded jar must ship the generated content; :content:jar already fails without it, this checks the
// result (a jar without /content starts but cannot load anything).
val verifyPluginContent = tasks.register("verifyPluginContent") {
    group = "verification"
    val jar = tasks.shadowJar.flatMap { it.archiveFile }
    inputs.file(jar)
    doLast {
        val required = listOf("content/data/id_map.json", "content/craftengine/tfp/pack.yml")
        val missing = ZipFile(jar.get().asFile).use { z -> required.filter { z.getEntry(it) == null } }
        check(missing.isEmpty()) {
            "${jar.get().asFile.name} lacks $missing: run :tools:datagen:generateContent (or tools/worktree/share-build.sh)"
        }
    }
}
tasks.shadowJar { finalizedBy(verifyPluginContent) }
tasks.named("check") { dependsOn(verifyPluginContent) }
