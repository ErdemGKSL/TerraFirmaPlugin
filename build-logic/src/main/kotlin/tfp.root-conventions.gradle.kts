// Root project: repository-wide checks (M1-BUILD-02).
val checkNotice = tasks.register<dev.tfp.build.CheckNotice>("checkNotice") {
    group = "verification"
    notice = layout.projectDirectory.file("NOTICE")
    val manifest = layout.projectDirectory.file("content/generated/content/third-party.json")
    if (manifest.asFile.exists()) thirdParty = manifest
    report = layout.buildDirectory.file("reports/notice.txt")
}
tasks.register("check") {
    group = "verification"
    dependsOn(checkNotice)
}
