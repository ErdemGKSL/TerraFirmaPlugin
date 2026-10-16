// CLI: pinned TFC checkout -> content/ + reports. Deterministic output.
plugins {
    id("tfp.java-conventions")
    application
}
dependencies {
    implementation(project(":core"))
    implementation(libs.gson)
}
application { mainClass = "dev.tfp.datagen.Datagen" }
