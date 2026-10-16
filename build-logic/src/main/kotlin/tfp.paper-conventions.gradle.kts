// Modules that run inside the server: compile against the lowest supported Paper API.
plugins { id("tfp.java-conventions") }

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    compileOnly(libs.findLibrary("paper-api").get())
    testImplementation(libs.findLibrary("paper-api").get())
}
