// Generated + copied data and assets (TFC JSON, CE pack, BetterHud pack, models).
// Produced by :tools:datagen, never edited by hand. Shipped as jar resources under /content.
plugins { `java-library` }
// The jar packs content/generated directly. A resources source set would first copy the whole tree (~110 MB) into
// build/resources/main in every worktree. content/generated may be a symlink to a read-only shared store entry
// (tools/worktree/share-build.sh); this module only reads it.
sourceSets.main { resources.setSrcDirs(emptyList<String>()) }
// PAR-INFRA-24: never a silent content-less jar. The plugin cannot start without these files
// ("content/data/id_map.json is missing from the plugin jar"), so the jar fails when datagen has not run.
val requiredContent = listOf("content/data/id_map.json", "content/craftengine/tfp/pack.yml")
val generatedDir = layout.projectDirectory.dir("generated")
tasks.jar {
    from(generatedDir)
    val required = requiredContent.map { generatedDir.file(it).asFile }
    doFirst {
        val missing = required.filter { !it.isFile }
        check(missing.isEmpty()) {
            "content/generated has no ${missing.joinToString { it.relativeTo(generatedDir.asFile).path }}: run " +
                "`./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc` (or tools/worktree/share-build.sh in a " +
                "worktree) before building the plugin jar"
        }
    }
}
