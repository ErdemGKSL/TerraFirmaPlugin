// Pure gameplay rules, data model, registries and codecs. No Bukkit/NMS/plugin imports.
plugins { id("tfp.pure-conventions") }
dependencies {
    api(libs.caffeine)
    api(libs.jctools)
    // Paper ships Gson at runtime; core only compiles against it.
    compileOnlyApi(libs.gson)
    testImplementation(libs.gson)
    // M1-INST-02 tests: check that edited third-party YAML still parses to the expected tree.
    testImplementation(libs.snakeyaml)
}

// M1-REG-04: ContentDataLoadTest loads the datagen output in content/generated/content/data (skipped while absent).
// Declared as a test input so `check` re-runs the test whenever datagen output changes.
val contentData = rootProject.layout.projectDirectory.dir("content/generated/content/data")
tasks.test {
    systemProperty("tfp.contentData", contentData.asFile.absolutePath)
    inputs.files(rootProject.fileTree(contentData))
        .withPropertyName("contentData")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// PAR-REL-02: JMH source set for core (core/src/jmh/java).
// Run: ./gradlew :core:jmh  (writes core/build/reports/jmh/results.json). Budgets: core/src/jmh/budgets.json, checked by
// core/src/jmh/check-budgets.py (`:core:jmhCheck`).
val jmhVersion = "1.37"

val jmh = sourceSets.create("jmh") {
    java.setSrcDirs(listOf("src/jmh/java"))
    compileClasspath += sourceSets["main"].output + configurations["runtimeClasspath"]
    runtimeClasspath += output + compileClasspath
}
dependencies {
    "jmhImplementation"("org.openjdk.jmh:jmh-core:$jmhVersion")
    "jmhAnnotationProcessor"("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")
}

val jmhResults = layout.buildDirectory.file("reports/jmh/results.json")
val jmhRun = tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "Runs the JMH benchmarks (PAR-REL-02). Pass -Pjmh.include=<regex> to select."
    classpath = jmh.runtimeClasspath
    mainClass = "org.openjdk.jmh.Main"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    val include = providers.gradleProperty("jmh.include").orElse("dev\\.tfp\\.core\\.perf\\..*")
    doFirst {
        jmhResults.get().asFile.parentFile.mkdirs()
        args(include.get(), "-rf", "json", "-rff", jmhResults.get().asFile.absolutePath)
    }
}
tasks.register<Exec>("jmhCheck") {
    group = "verification"
    description = "Fails when a JMH result is more than 20 % above its budget in core/src/jmh/budgets.json."
    dependsOn(jmhRun)
    commandLine("python3", "-I", "src/jmh/check-budgets.py", "src/jmh/budgets.json", jmhResults.get().asFile.absolutePath)
}
tasks.named("check") { dependsOn(tasks.named("compileJmhJava")) }
