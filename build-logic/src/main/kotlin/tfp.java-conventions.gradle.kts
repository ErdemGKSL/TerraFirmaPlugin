// Every Java module: Java 25, UTF-8, JUnit 5 + AssertJ.
plugins { `java-library` }

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(name: String) = libs.findLibrary(name).get()

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial,-classfile"))
}

dependencies {
    testImplementation(platform(lib("junit-bom")))
    testImplementation(lib("junit-jupiter"))
    testImplementation(lib("assertj"))
    testRuntimeOnly(lib("junit-launcher"))
}

tasks.test {
    useJUnitPlatform()
    // Thread assertions on in tests (ThreadAsserts reads this).
    systemProperty("tfp.assertThreads", "true")
}
