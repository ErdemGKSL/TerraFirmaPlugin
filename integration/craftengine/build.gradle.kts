// The only module that imports the craftengine API.
plugins { id("tfp.paper-conventions") }
dependencies {
    implementation(project(":paper"))
    compileOnly(libs.craftengine)
    // M1-SVC-01: controller/NBT codec tests use CraftEngine's NBT classes directly (no server needed).
    testImplementation(libs.craftengine)
}
