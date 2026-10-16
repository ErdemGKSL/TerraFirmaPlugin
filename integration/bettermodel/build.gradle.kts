// The only module that imports the bettermodel API.
plugins { id("tfp.paper-conventions") }
dependencies {
    implementation(project(":paper"))
    compileOnly(libs.bettermodel)
}
