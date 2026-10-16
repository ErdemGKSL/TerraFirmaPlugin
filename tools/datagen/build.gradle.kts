// CLI: pinned TFC checkout -> content/ + reports. Deterministic output.
plugins {
    id("tfp.java-conventions")
    application
}
dependencies {
    implementation(project(":core"))
    implementation(libs.gson)
    testImplementation(libs.snakeyaml)
}
application { mainClass = "dev.tfp.datagen.Datagen" }

// Checkout location: -Ptfc.checkout=..., else $TFC_CHECKOUT, else /opt/tfc. The pinned commit is checked by the CLI.
val tfcCheckout = providers.gradleProperty("tfc.checkout")
    .orElse(providers.environmentVariable("TFC_CHECKOUT"))
    .orElse("/opt/tfc")
// The content module ships content/generated as jar resources, so this directory becomes /content in the jar.
val contentDir = rootProject.layout.projectDirectory.dir("content/generated/content")

tasks.test {
    // The full-checkout test runs only when the pinned checkout is present.
    systemProperty("tfc.checkout", tfcCheckout.get())
    systemProperty("tfp.repoRoot", rootProject.layout.projectDirectory.asFile.absolutePath)
}

tasks.register<JavaExec>("generateContent") {
    group = "datagen"
    description = "Regenerates content/generated/content from the pinned TFC checkout."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.tfp.datagen.Datagen"
    args(tfcCheckout.get(), contentDir.asFile.absolutePath)
}

tasks.register<JavaExec>("verifyContent") {
    group = "verification"
    description = "Fails if content/generated/content differs from a fresh datagen run (CI gate)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.tfp.datagen.Datagen"
    args(tfcCheckout.get(), contentDir.asFile.absolutePath, "--check")
}
