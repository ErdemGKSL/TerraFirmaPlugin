// Pure gameplay rules, data model, registries and codecs. No Bukkit/NMS/plugin imports.
plugins { id("tfp.pure-conventions") }
dependencies {
    api(libs.caffeine)
    api(libs.jctools)
    // Paper ships Gson at runtime; core only compiles against it.
    compileOnlyApi(libs.gson)
    testImplementation(libs.gson)
}

// M1-REG-04: ContentDataLoadTest loads the datagen output in content/generated/content/data (skipped while absent).
// Declared as a test input so `check` re-runs the test whenever datagen output changes.
val contentData = rootProject.layout.projectDirectory.dir("content/generated/content/data")
tasks.test {
    systemProperty("tfp.contentData", contentData.asFile.absolutePath)
    inputs.files(rootProject.fileTree(contentData))
        .withPropertyName("contentData")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
