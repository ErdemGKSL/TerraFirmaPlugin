// M0-JAR-01: one jar, compiled once against the lowest Paper API, run on 1.21.11 and 26.3.
// Throwaway spike code (plans/00-m0-spikes.md). Not production.
plugins {
    java
}

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

dependencies {
    // Lowest supported API. The same jar must run on 26.3 (api-version '1.21.11').
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // Loaded at runtime by Jar01Loader through MavenLibraryResolver, NOT bundled in the jar.
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.2.2")
}
