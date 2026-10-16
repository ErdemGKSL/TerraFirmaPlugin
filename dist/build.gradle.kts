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
