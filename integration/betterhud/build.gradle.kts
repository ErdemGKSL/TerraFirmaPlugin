// The only module that imports the betterhud API.
plugins { id("tfp.paper-conventions") }
dependencies {
    implementation(project(":paper"))
    compileOnly(libs.betterhud)
    // PlaceholderProbeTest: the API interfaces (HudPlaceholder.of, HudPlayer) without a running BetterHud.
    testImplementation(libs.betterhud)
}
