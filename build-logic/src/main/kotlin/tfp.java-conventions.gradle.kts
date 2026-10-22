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

// Each test task gets its own java.io.tmpdir (JUnit @TempDir, Files.createTempDirectory) under its module's build
// directory, so test tasks that run in parallel (org.gradle.parallel) never share or clean up each other's temp files.
// Passed through an argument provider so the absolute path is not a build-cache input.
tasks.withType<Test>().configureEach {
    val tmpDir = layout.buildDirectory.dir("tmp/$name-java-tmp").get().asFile
    doFirst { tmpDir.mkdirs() }
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Djava.io.tmpdir=${tmpDir.absolutePath}") })
}

// EUPL headers on every Java source; ported files name the pinned TFC commit (M1-BUILD-02).
val ledgerTfcCommit = providers.fileContents(rootProject.layout.projectDirectory.file("docs/porting/ledger.md"))
    .asText.map { Regex("""\b[0-9a-f]{40}\b""").find(it)?.value ?: error("no pinned TFC commit in docs/porting/ledger.md") }
val checkLicenseHeaders = tasks.register<dev.tfp.build.CheckLicenseHeaders>("checkLicenseHeaders") {
    group = "verification"
    sources.from(fileTree("src") { include("**/*.java") })
    pinnedTfcCommit = ledgerTfcCommit
    report = layout.buildDirectory.file("reports/license-headers.txt")
}
tasks.named("check") { dependsOn(checkLicenseHeaders) }
