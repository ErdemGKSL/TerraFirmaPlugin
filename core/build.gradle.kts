// Pure gameplay rules, data model, registries and codecs. No Bukkit/NMS/plugin imports.
plugins { id("tfp.pure-conventions") }
dependencies {
    api(libs.caffeine)
    api(libs.jctools)
    // Paper ships Gson at runtime; core only compiles against it.
    compileOnlyApi(libs.gson)
    testImplementation(libs.gson)
}
