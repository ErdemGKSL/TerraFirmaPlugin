plugins { `kotlin-dsl` }
dependencies {
    // Make plugins from the catalog available to the convention scripts.
    implementation("com.gradleup.shadow:com.gradleup.shadow.gradle.plugin:${libs.versions.shadow.get()}")
    implementation("xyz.jpenilla.run-paper:xyz.jpenilla.run-paper.gradle.plugin:${libs.versions.run.paper.get()}")
}
dependencies { implementation("com.google.code.gson:gson:${libs.versions.gson.get()}") }
