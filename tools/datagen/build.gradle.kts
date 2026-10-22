import java.io.File
import java.nio.file.Files

// CLI: pinned TFC checkout -> content/ + reports. Deterministic output.
plugins {
    id("tfp.java-conventions")
    application
}
// PAR-INFRA-14: the NeoForge universal jar TFC pins; CommonTags reads its data/c/tags. Datagen-only, never shipped.
val neoforgeJar: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    implementation(project(":core"))
    implementation(libs.gson)
    testImplementation(libs.snakeyaml)
    neoforgeJar("${libs.neoforge.get()}:universal@jar")
}
application { mainClass = "dev.tfp.datagen.Datagen" }

// Passes the jar as -Dtfp.neoforgeJar=<path> and tracks it as an input.
fun JavaForkOptions.neoforgeJarProperty(task: Task) {
    task.inputs.files(neoforgeJar).withPropertyName("neoforgeJar").withPathSensitivity(PathSensitivity.NONE)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dtfp.neoforgeJar=" + neoforgeJar.singleFile.absolutePath)
    })
}

// Checkout location: -Ptfc.checkout=..., else $TFC_CHECKOUT, else /opt/tfc. The pinned commit is checked by the CLI.
val tfcCheckout = providers.gradleProperty("tfc.checkout")
    .orElse(providers.environmentVariable("TFC_CHECKOUT"))
    .orElse("/opt/tfc")
// The content module ships content/generated as jar resources, so this directory becomes /content in the jar.
val contentDir = rootProject.layout.projectDirectory.dir("content/generated/content")

tasks.test {
    // The full-checkout test holds the whole generated tree (~80 MB) and parses 20 visual allocations. A bounded
    // heap makes the GC collect early instead of growing towards 1/4 of RAM (the CI/container memory limit).
    maxHeapSize = "1g"
    // The full-checkout test runs only when the pinned checkout is present.
    systemProperty("tfc.checkout", tfcCheckout.get())
    systemProperty("tfp.repoRoot", rootProject.layout.projectDirectory.asFile.absolutePath)
    neoforgeJarProperty(this)
}

// Output of generateContent: -Ptfp.contentDir=<dir> (tools/worktree/share-build.sh builds shared store entries this
// way), else this worktree's content/generated/content.
val contentOut = providers.gradleProperty("tfp.contentDir").map { file(it) }.orElse(contentDir.asFile)
val generatedRoot = rootProject.layout.projectDirectory.dir("content/generated").asFile

tasks.register<JavaExec>("generateContent") {
    group = "datagen"
    description = "Regenerates content/generated/content (or -Ptfp.contentDir) from the pinned TFC checkout."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.tfp.datagen.Datagen"
    val out = contentOut.get()
    val useDefault = out.absoluteFile == contentDir.asFile.absoluteFile
    val link = generatedRoot.toPath()
    args(tfcCheckout.get(), out.absolutePath)
    neoforgeJarProperty(this)
    doFirst {
        // content/generated may be a symlink to a read-only shared store entry (share-build.sh). Regenerating
        // unshares it: drop the link (never the entry) and write a real directory in this worktree.
        if (useDefault && Files.isSymbolicLink(link)) {
            Files.delete(link)
            logger.lifecycle("generateContent: content/generated was a shared link; writing a private copy")
        }
        var p: File? = out.absoluteFile
        while (p != null) {
            if (File(p, ".tfp-shared-entry").exists()) {
                throw GradleException("refusing to write into the shared store entry $p; drop -Ptfp.contentDir")
            }
            p = p.canonicalFile.parentFile
        }
    }
}

tasks.register<JavaExec>("verifyContent") {
    group = "verification"
    description = "Fails if content/generated/content differs from a fresh datagen run (CI gate)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.tfp.datagen.Datagen"
    args(tfcCheckout.get(), contentDir.asFile.absolutePath, "--check")
    neoforgeJarProperty(this)
}

// Lists watched TFC paths changed since docs/porting/tfc-pin.txt (offline; needs a TFC clone with history).
tasks.register<JavaExec>("tfcPinDrift") {
    group = "verification"
    description = "Lists TFC Java and data paths changed since the pin in docs/porting/tfc-pin.txt."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.tfp.datagen.TfcPinDrift"
    args(tfcCheckout.get(), rootProject.layout.projectDirectory.file("docs/porting/tfc-pin.txt").asFile.absolutePath)
}
