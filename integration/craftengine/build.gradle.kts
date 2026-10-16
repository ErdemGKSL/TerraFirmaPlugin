// The only module that imports the craftengine API.
plugins { id("tfp.paper-conventions") }
dependencies {
    implementation(project(":paper"))
    compileOnly(libs.craftengine)
}
