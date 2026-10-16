# Paper/Folia plugin tooling and testing for Minecraft 1.21.x and 26.x (state as of 2026-10-16)

> Note for this repo: this is the raw research report (2026-10-16). The project-level conclusions drawn from it (Java 25 everywhere, because BetterModel/BetterHud require it; version targets) are in `CLAUDE.md` and the `plugin-testing` / `paper-folia-threading` skills, and take precedence where they differ (e.g. §1.5 suggests release 21 bytecode, which BetterModel/BetterHud make moot).

Research for TerraFirmaPlugin, an EUPL-1.2 Paper and Folia plugin that reimplements TerraFirmaCraft on the server side. Data was collected on **2026-10-16**. Each claim cites a URL or a repo file. Where this report says "repo file", it means a file in a shallow clone of the named GitHub repository made on that date. The commit is given where it matters.

Items marked **UNVERIFIED** could not be confirmed from a primary source.

---

## Table of contents

1. [Version matrix](#1-version-matrix)
   - 1.1 Minecraft releases
   - 1.2 Paper and Folia builds per version
   - 1.3 Java requirements
   - 1.4 Obfuscation, mappings, and what they mean for NMS
   - 1.5 Building one jar for 1.21.x and 26.x
2. [Build tooling](#2-build-tooling)
   - 2.1 Tool versions
   - 2.2 Paper API coordinates and repository
   - 2.3 paperweight-userdev
   - 2.4 `paper-plugin.yml` vs `plugin.yml`
   - 2.5 Shadow
   - 2.6 run-paper (`runServer`, `runFolia`, plugin downloads)
   - 2.7 Reference multi-module layout
3. [Paper APIs relevant to TFC](#3-paper-apis-relevant-to-tfc)
4. [Folia](#4-folia)
5. [Testing](#5-testing)
6. [Libraries and what Paper already bundles](#6-libraries-and-what-paper-already-bundles)
7. [Recommendations summary](#7-recommendations-summary)
8. [UNVERIFIED items and open questions](#8-unverified--open-questions)
9. [Source index](#9-source-index)

---

## 1. Version matrix

### 1.1 Minecraft releases (Mojang version manifest)

Source: `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`, fetched 2026-10-16. `latest.release = 26.3` and `latest.snapshot = 26.4-snapshot-4`.

| Version | Release time (UTC) | `javaVersion.majorVersion` | Mappings in `downloads` |
|---|---|---|---|
| 1.21.11 | 2025-12-09 | 21 | `client_mappings`, `server_mappings` present |
| 26.1 | 2026-03-24 | 25 | **none** (`client`, `server` only) |
| 26.1.1 | 2026-04-01 | (25) | – |
| 26.1.2 | 2026-04-09 | 25 | none |
| 26.2 | 2026-06-16 | 25 | none |
| 26.3 | 2026-09-15 | 25 | none |
| 26.4 | snapshots only (`26.4-snapshot-4`) | – | – |

Per-version JSON files:
- 26.3: https://piston-meta.mojang.com/v1/packages/11f1aefe473eefba0af62e3286c33b692807b2eb/26.3.json
- 26.2: https://piston-meta.mojang.com/v1/packages/91ef51b7d36ca092c5c02aba42f0f40c7cc1db84/26.2.json
- 26.1: https://piston-meta.mojang.com/v1/packages/8667c5278895a6ae31e05ab99ba044ef33fca092/26.1.json
- 1.21.11: https://piston-meta.mojang.com/v1/packages/718cd84b4912691fe5de829aab6876a4b7f6c5c4/1.21.11.json

1.21.x releases in the manifest: 1.21, 1.21.1 through 1.21.11. There is a 1.21.2, but Paper skipped it (see 1.2).

### 1.2 Paper and Folia builds per version (PaperMC Fill API v3)

Sources:
- `https://fill.papermc.io/v3/projects/paper`
- `https://fill.papermc.io/v3/projects/folia`
- `.../versions/<v>` and `.../versions/<v>/builds/latest`

All fetched 2026-10-16.

| MC version | Paper support status | Latest Paper build (channel, date) | Folia | Latest Folia build (channel, date) |
|---|---|---|---|---|
| 26.3 | SUPPORTED | #194 STABLE, 2026-10-15 | SUPPORTED | **#3 ALPHA**, 2026-10-14 |
| 26.2 | SUPPORTED | #134 STABLE, 2026-10-14 | DEPRECATED | #7 BETA, 2026-08-25 |
| 26.1.2 | UNSUPPORTED (end 2026-07-26) | builds up to #74 | UNSUPPORTED (end 2026-10-14) | builds 6–8 |
| 26.1.1 | listed | – | not built | – |
| 26.1 | **not built** (Paper started at 26.1.1) | – | – | – |
| 1.21.11 | UNSUPPORTED (end 2026-06-15) | #132 STABLE, 2026-05-11 | UNSUPPORTED (end 2026-10-14) | #14 STABLE, 2026-02-22 |
| 1.21.10 / 1.21.9 | listed (unsupported) | – | **no Folia** | – |
| 1.21.8 | UNSUPPORTED (end 2025-11-14) | #60 | yes (end 2025-12-31) | #6 |
| 1.21.7 | listed | – | no Folia | – |
| 1.21.4–1.21.6 | listed | – | 1.21.4, 1.21.5, 1.21.6 | – |
| 1.21.3, 1.21.1, 1.21 | listed (no 1.21.2) | – | no Folia | – |

What the table means for the project:
- **Only Paper 26.2 and 26.3 are currently supported.** Paper 1.21.11 left support on 2026-06-15.
- **The only stable Folia line is 1.21.11, and it is unsupported.** Folia 26.3 is ALPHA. Folia 26.2 is BETA and DEPRECATED.
- In practice, a "1.21.x + 26.x" target means **1.21.11 + 26.2/26.3**. Folia has no 1.21.9 or 1.21.10 builds.
- PaperMC announcement posts:
  - 26.1: https://papermc.io/news/26-1 (2026-04-26)
  - 26.2: https://papermc.io/news/26-2 (2026-06-12)
  - 26.3: https://papermc.io/news/26-3 (2026-09-26)
  - 1.21.11: https://papermc.io/news/1-21-11 (2025-12-21)
- Per the 26.x posts, worlds cannot be downgraded after an upgrade to 26.1, 26.2 or 26.3. The 26.1 post also says it changes the world storage layout. Dimensions move under `world/dimensions/minecraft/`.

### 1.3 Java requirements

| Target | Minimum Java | Evidence |
|---|---|---|
| Paper/Folia 1.21.x | **21** | Fill API `java.version.minimum: 21` for 1.21.11, 1.21.8 and 1.21.4. Mojang manifest `javaVersion.majorVersion: 21` for 1.21.11. The `paper-api 1.21.11-R0.1-SNAPSHOT` jar is class-file major **65** (Java 21), checked with `javap` on `org/bukkit/Server.class`. |
| Paper/Folia 26.x | **25** | Fill API `java.version.minimum: 25` for 26.1.2, 26.2 and 26.3. Mojang manifest `majorVersion: 25`. `paper-api 26.3.build.194-stable` is class-file major **69** (Java 25). The Paper docs toolchain snippet uses `JavaLanguageVersion.of(25)` (https://docs.papermc.io/paper/dev/project-setup/). |

Consequence: code that must load on 1.21.11 servers has to be compiled with `--release 21`. Code that is only loaded on 26.x may use `--release 25`.

### 1.4 Obfuscation, mappings, and what they mean for NMS

- **Mojang stopped obfuscating Java Edition.**
  - The announcement is "Removing obfuscation in Java Edition" on minecraft.net (https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition). It returned HTTP 503 when fetched. Localized copies exist, such as https://www.minecraft.net/de-de/article/removing-obfuscation-in-java-edition. Secondary coverage: https://adtmag.com/articles/2025/11/04/mojang-studios-to-remove-code-obfuscation-from-minecraft-java-edition.aspx.
  - Primary evidence: none of the 26.1, 26.2 or 26.3 version JSONs has `server_mappings` or `client_mappings` (table 1.1).
- **Paper's runtime has been Mojang-mapped since 1.20.5.** The userdev docs say: "As of 1.20.5, Paper ships with a Mojang-mapped runtime instead of re-obfuscating the server to Spigot mappings" (https://docs.papermc.io/paper/dev/userdev/).
- **26.1 and later:**
  - The userdev docs say "Since Minecraft version **26.1**, Paper no longer supports remapping your plugin to Spigot runtime mappings."
  - They also say "Paper no longer supports obfuscated plugins due to Mojang themselves removing obfuscation of their server JAR … the reobfuscation function no longer works for dev bundles for 26.1+."
  - The 26.1 news post says Paper "dropped its internal remapper. Plugins that use server internals, directly or through reflection, are affected" (https://papermc.io/news/26-1).
- **What this means for the project:**
  - On 26.x there is no `reobfJar` and no plugin remapping. You compile against the dev bundle, and the class and member names you see are the runtime names.
  - On 1.20.5–1.21.11, Paper still has a plugin remapper. A jar that declares it is Mojang-mapped skips remapping. paperweight writes the `paperweight-mappings-namespace: mojang` manifest attribute when `reobfArtifactConfiguration = MOJANG_PRODUCTION`. The official multi-version example sets this attribute by hand on its shaded root jar (paperweight-test-plugin `multi-project` branch, `build.gradle.kts`, https://github.com/PaperMC/paperweight-test-plugin/tree/multi-project).
  - The CraftBukkit package relocation (`org.bukkit.craftbukkit.v1_xx_Rx`) was dropped in 1.20.5 (PSA: https://papermc.io/news/important-dev-psa-future-removal-of-cb-package-relocation). CB classes therefore have the same package on 1.21.11 and 26.x.
  - Whether Mojang-mapped names on 1.21.11 and the "real" names on 26.x are identical for the classes we touch is **UNVERIFIED**. Mojang mappings were generated from the original names, so they are expected to match, but signatures change between versions anyway. Keep NMS in per-version modules.
- run-paper's `disablePluginRemapping()` sets `paper.disablePluginRemapping=true`. It "Only has an effect on Paper 1.20.5 and newer builds that include plugin remapping" (run-task repo, `plugin/src/main/kotlin/xyz/jpenilla/runpaper/task/RunServer.kt`).

### 1.5 Building one jar for 1.21.x and 26.x

There are two workable strategies.

**A. Avoid NMS. This is the strongly preferred default.**
- Compile the shared Paper module against `paper-api:1.21.11-R0.1-SNAPSHOT` with `--release 21`. Everything listed below already exists in that API jar (verified with `unzip -l` and `javap` on the 1.21.11 API jar from `repo.papermc.io`):
  - `LifecycleEvents.{COMMANDS, TAGS, DATAPACK_DISCOVERY}`
  - `DatapackRegistrar`
  - `RegistryEvents` (the same 15 providers as 26.3 except TRIM_MATERIAL, TRIM_PATTERN and SULFUR_CUBE_ARCHETYPE, which are 26.2+/26.3)
  - Folia schedulers and 9 `isOwnedByCurrentRegion` overloads
  - `ServerBuildInfo`
  - `MobGoals`
  - Brigadier `Commands`
  - `MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR`
  - `Biome` as a registry-backed interface rather than an enum
- Do packet work through PacketEvents. It supports 1.21–1.21.11 and 26.1–26.3: `ServerVersion.java` has `V_1_21_11(774)`, `V_26_1(775)`, `V_26_2(776)` and `V_26_3(777)` (retrooper/packetevents repo, `api/src/main/java/com/github/retrooper/packetevents/manager/server/ServerVersion.java`).
- Set `api-version` to the **lowest** version you support. Paper rejects a plugin whose api-version is newer than the server: CraftMagicNumbers throws `"Unsupported API version"` when `toCheck.isNewerThan(ApiVersion.CURRENT)` (Paper repo, `paper-server/src/main/java/org/bukkit/craftbukkit/util/CraftMagicNumbers.java`, lines ~373–378).

**B. Per-version NMS modules, for whatever cannot be done through the API.** This is the pattern from PaperMC's own example (https://github.com/PaperMC/paperweight-test-plugin/tree/multi-project):
- Apply `io.papermc.paperweight.userdev` with `apply false` in the root project. Applying it in more than one project otherwise hits a Gradle bug (README of the same repo).
- Add one subproject per Minecraft version, each with `paperweight.paperDevBundle("<ver>")` and its own `options.release`.
- The root project shades all of them with `runtimeOnly(project(":paper_26_1_2"))`. Use the plain variant for 1.20.5+, and the `reobf` configuration only for older versions or Spigot.
- Call `java.disableAutoTargetJvm()` so a release-21 root can consume a release-25 subproject.
- Set the manifest attribute `"paperweight-mappings-namespace" to "mojang"`.
- At runtime, choose the implementation from `ServerBuildInfo.buildInfo().minecraftVersionId()` (Paper API `io/papermc/paper/ServerBuildInfo.java`). Never load release-25 classes on a Java 21 server.

Adventure caveat for a single jar:
- 1.21.11 bundles Adventure **4.26.1**, and 26.3 bundles **5.2.0** (`META-INF/libraries.list` inside both Paper server jars).
- The 26.2 news post says "Paper 26.2 ships with Adventure 5, removing API that was previously deprecated", for example `BookMeta` no longer extending `Book`, and deprecated ClickEvent/HoverEvent usage being removed (https://papermc.io/news/26-2).
- Adventure 5.2.0 restores binary compatibility for code "compiling against 4.x but running against 5.x" (`BuildableComponent` bridges), with bridges "removed in 6.x" (https://github.com/PaperMC/adventure/releases/tag/v5.2.0).
- Migration guide: https://docs.papermc.io/adventure/migration/adventure-4.x/.
- So: compile against 4.x and avoid APIs that 5.x removed, or put Adventure-sensitive code behind the version split.

---

## 2. Build tooling

### 2.1 Tool versions (looked up 2026-10-16)

| Tool | Coordinates / ID | Latest | Source |
|---|---|---|---|
| Gradle | – | 9.8.1 | https://services.gradle.org/versions/current |
| paperweight-userdev | `io.papermc.paperweight.userdev` | **2.0.0-beta.24** | plugins.gradle.org maven-metadata; docs snippet https://docs.papermc.io/paper/dev/userdev/ |
| run-paper | `xyz.jpenilla.run-paper` | **3.1.0** | plugins.gradle.org maven-metadata (repo master is `3.1.1-SNAPSHOT`) |
| Shadow | `com.gradleup.shadow` | **9.6.1** | plugins.gradle.org maven-metadata; 9.5.0+ needs Gradle ≥ 9.2 and Java ≥ 17 (https://gradleup.com/shadow/) |
| resource-factory | `xyz.jpenilla.resource-factory-paper-convention` / `-bukkit-convention` | 1.4.0 | plugins.gradle.org |
| foojay toolchain resolver | `org.gradle.toolchains.foojay-resolver-convention` | 1.0.0 | plugins.gradle.org |
| JMH Gradle plugin | `me.champeau.jmh` | 0.7.3 | plugins.gradle.org |
| jcstress Gradle plugin | `io.github.reyerizo.gradle.jcstress` | 1.0.0 | plugins.gradle.org (only the metadata was checked) |
| paper-api | `io.papermc.paper:paper-api` | `26.3.build.194-stable` / `1.21.11-R0.1-SNAPSHOT` | repo.papermc.io maven-metadata |
| folia-api | `dev.folia:folia-api` | `26.3.build.3-alpha`, `26.2.build.7-beta` | repo.papermc.io maven-metadata |
| dev bundle (Paper) | `io.papermc.paper:dev-bundle` | 26.3.build.*, 26.2.build.*, 26.1.2.build.*, `1.21.11-R0.1-SNAPSHOT` | repo.papermc.io |
| dev bundle (Folia) | `dev.folia:dev-bundle` | up to `26.3.build.3-alpha` | repo.papermc.io |
| MockBukkit | `org.mockbukkit.mockbukkit:mockbukkit-v26.2` | 4.117.0 (2026-10-06) | Maven Central (Google mirror) |
| MockBukkit (1.21) | `org.mockbukkit.mockbukkit:mockbukkit-v1.21` | 4.116.3 (2026-08-19) | Maven Central |
| PacketEvents | `com.github.retrooper:packetevents-spigot` | 2.14.0 (supports 26.3) | repo.codemc.io; CHANGELOG.md |
| JUnit Jupiter | `org.junit.jupiter:junit-jupiter` | 6.1.3 | Maven Central |
| fastutil | `it.unimi.dsi:fastutil` | 8.5.19 (Paper bundles 8.5.18) | Maven Central |
| Caffeine | `com.github.ben-manes.caffeine:caffeine` | 3.3.1 | Maven Central |
| JCTools | `org.jctools:jctools-core` | 4.0.7 | Maven Central |
| JMH | `org.openjdk.jmh:jmh-core` | 1.37 | Maven Central |
| jcstress | `org.openjdk.jcstress:jcstress-core` | 0.16 | Maven Central |
| Awaitility | `org.awaitility:awaitility` | 4.3.0 | Maven Central |
| GitHub Actions | `actions/checkout` v7.0.1, `actions/setup-java` v6.1.0, `gradle/actions` v6.4.0, `actions/upload-artifact` v7.0.2 | – | `git ls-remote --tags` |

### 2.2 Paper API coordinates and repository

From https://docs.papermc.io/paper/dev/project-setup/:

```kotlin
repositories {
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")   // 26.x: "<mc>.build.<n>-<channel>"
    // 1.21.11 and below: "1.21.11-R0.1-SNAPSHOT" (no way to pin a build)
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
```

- The version scheme changed in 26.1. The 26.1 post says to use `26.1.2.build.+` and to "keep `build` in the string, or `26.1.+` could resolve to a breaking release like 26.1.1" (https://papermc.io/news/26-1).
- A specific build looks like `26.3.build.194-stable`.
- For reproducible CI, **pin the exact build** instead of `+`.
- For Folia-only API, use `dev.folia:folia-api:[26.3.build,)` (Folia README). The Folia schedulers and `isOwnedByCurrentRegion` are already in `paper-api`, so `folia-api` is normally not needed (Paper repo `paper-api/src/main/java/org/bukkit/Server.java` lines 2682–2800).

### 2.3 paperweight-userdev

From https://docs.papermc.io/paper/dev/userdev/:

```kotlin
plugins { id("io.papermc.paperweight.userdev") version "2.0.0-beta.24" }
dependencies {
    paperweight.paperDevBundle("26.3.build.+")   // includes paper-api; remove a separate paper-api dep
    // paperweight.foliaDevBundle("26.3.build.+")
}
// For 1.20.5..1.21.11 only, to mark the jar Mojang-mapped (no Spigot reobf):
paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
```

- `reobfJar` and `REOBF_PRODUCTION` exist only for Spigot or pre-1.20.5 targets. They "no longer work for dev bundles for 26.1+" (same page).
- If the dev bundle cannot run on the Gradle toolchain, set `paperweight { javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(N) } }` (same page).
- The test plugin README says paperweight automatically uses `shadowJar` as the input to `reobfJar` when Shadow is applied. It also recommends `jpenilla/reflection-remapper` for reflection, which is moot on 26.x (https://github.com/PaperMC/paperweight-test-plugin).

### 2.4 `paper-plugin.yml` vs `plugin.yml`

Docs: https://docs.papermc.io/paper/dev/getting-started/paper-plugins/. The page labels Paper plugins "Experimental".

| Feature | `plugin.yml` (Bukkit) | `paper-plugin.yml` |
|---|---|---|
| `bootstrapper` (`PluginBootstrap`, runs before the server and registries exist) | no | yes |
| `loader` (`PluginLoader` builds the classpath, e.g. Maven libraries) | `libraries:` list | `PluginLoader` + `MavenLibraryResolver`/`JarLibrary` |
| Registry modification and datapack discovery in bootstrap | no | yes (only Paper plugins, per https://docs.papermc.io/paper/dev/registries/) |
| Commands in YAML | yes | **no**, use the Brigadier lifecycle API |
| Dependencies | `depend`/`softdepend`/`loadbefore` | `dependencies.{bootstrap,server}.<Name>.{load: BEFORE/AFTER/OMIT, required, join-classpath}` |
| Classloader isolation | shared | isolated; `join-classpath: true` gives access to the other plugin's classes |
| Cyclic dependencies | resolved | **server fails to start** |
| `folia-supported: true` | yes | yes |
| `load: STARTUP` | yes | yes; `PaperPluginMeta` has `load` defaulting to `POSTWORLD` and `has-open-classloader` (Paper repo `paper-server/src/main/java/io/papermc/paper/plugin/provider/configuration/PaperPluginMeta.java`) |

Folia support in `paper-plugin.yml` comes from Folia's patch to `PaperPluginMeta`, which adds `private boolean foliaSupported` (Folia repo `folia-server/paper-patches/features/0001-Region-Threading-Base.patch`, ~line 709). The flag in `plugin.yml` comes from `folia-api/paper-patches/features/0003-Require-plugins-to-be-explicitly-marked-as-Folia-sup.patch`.

The default `load` of POSTWORLD matters for worldgen. Worlds created by the server at startup only get a plugin's generator if the plugin loads at **STARTUP**, so set `load: STARTUP`. The generator is reached through `Plugin#getDefaultWorldGenerator(String, String)`, which `WorldCreator` calls (Paper repo `paper-api/.../WorldCreator.java:654`). That loading rule is general Bukkit behaviour and is **UNVERIFIED** in docs for Paper plugins. Folia matters here too: `createWorld` and `unloadWorld` throw `UnsupportedOperationException`, as covered in §4.

Suggested `paper-plugin.yml`:

```yaml
name: TerraFirmaPlugin
version: '${version}'
main: dev.tfp.paper.TfpPlugin
bootstrapper: dev.tfp.paper.TfpBootstrap
loader: dev.tfp.paper.TfpLoader
api-version: '1.21.11'      # lowest supported; a newer value is rejected by older servers
load: STARTUP               # needed for bukkit.yml `generator:` on the default worlds
folia-supported: true
dependencies:
  bootstrap:
    CraftEngine: { load: BEFORE, required: true, join-classpath: true }   # only if bootstrap needs its classes
  server:
    CraftEngine:  { load: BEFORE, required: true,  join-classpath: true }
    BetterModel:  { load: BEFORE, required: true,  join-classpath: true }
    BetterHud:    { load: BEFORE, required: false, join-classpath: true }
    packetevents: { load: BEFORE, required: true,  join-classpath: true }
```

The plugin names `CraftEngine`, `BetterModel`, `BetterHud` and `packetevents` are **UNVERIFIED**. Read the `name:` from each jar's plugin.yml or paper-plugin.yml. The format of `api-version` for patch releases (`'1.21.11'` vs `'1.21'`) is also **UNVERIFIED**. The docs example uses `'26.3'`.

Library loader (`PluginLoader`):

```java
public final class TfpLoader implements PluginLoader {
  @Override public void classloader(PluginClasspathBuilder cp) {
    MavenLibraryResolver r = new MavenLibraryResolver();
    r.addRepository(new RemoteRepository.Builder("central", "default",
        MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
    r.addDependency(new Dependency(new DefaultArtifact("com.github.ben-manes.caffeine:caffeine:3.3.1"), null));
    r.addDependency(new Dependency(new DefaultArtifact("org.jctools:jctools-core:4.0.7"), null));
    cp.addLibrary(r);
  }
}
```

- `MAVEN_CENTRAL_DEFAULT_MIRROR` resolves from the env var `PAPER_DEFAULT_CENTRAL_REPOSITORY`, then the system property `org.bukkit.plugin.java.LibraryLoader.centralURL`, then `https://maven-central.storage-download.googleapis.com/maven2`.
- Using Maven Central directly logs "Use of Maven Central as a CDN is against the Maven Central Terms of Service" (Paper repo `paper-api/.../MavenLibraryResolver.java` lines 61, 125, 156–163).
- The constant exists in the 1.21.11 API too (`javap`).

### 2.5 Shadow

- Plugin `id("com.gradleup.shadow") version "9.6.1"` (https://gradleup.com/shadow/getting-started/).
- Relocation: `relocate("junit.framework", "shadow.junit")`. Auto-relocation: `enableAutoRelocation = true` with `relocationPrefix` (https://gradleup.com/shadow/configuration/relocation/).
- For Shadow 9 service-file merging, the PaperMC example uses:

```kotlin
tasks.shadowJar {
  mergeServiceFiles()
  filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
}
```

(paperweight-test-plugin `multi-project/build.gradle.kts`).

- **Do not shade** anything Paper already bundles (see §6): fastutil, Guava, Gson, Adventure, Brigadier, JOML, SnakeYAML, slf4j, log4j-api.
- Do not shade the dependency plugins (CraftEngine, BetterModel, BetterHud, PacketEvents). Use `compileOnly` and declare a plugin dependency.

### 2.6 run-paper

Source: https://github.com/jpenilla/run-task (README and `plugin/src/main/kotlin/...`, last commit 2026-08-08).

- **Tasks:**
  - `runServer` (Paper).
  - `runFolia`, which is opt-in via `runPaper.folia.registerTask()`. Its `pluginsMode` options are `INHERIT_ALL`, `INHERIT_NONE` and `PLUGIN_JAR_DETECTION` (`RunPaperExtension.kt`).
  - `cleanFoliaCache`.
- **Behaviour:**
  - Downloads use the **Fill API v3** (`DownloadsAPIService.kt`).
  - Plugin jars are passed as `-add-plugin=` (≥1.16.5).
  - `--nogui` is added (≥1.15).
  - `disable.watchdog=true` is set.
  - `ignoreUnsupportedJvm()` sets `Paper.IgnoreJavaVersion`.
  - Source: `RunServer.kt`.
- **Plugin downloads:**
  - `downloadPlugins { hangar(name, version); modrinth(id, version); github(owner, repo, tag, asset); url(url) }` (`DownloadPluginsSpec.kt`).
  - Modrinth resolves `GET /v2/project/{id}/version/{version}` (`PluginDownloadServiceImpl.kt:159`). Modrinth's API accepts either a version ID or a version number there; that is per Modrinth API docs and **UNVERIFIED** here.
- **EULA:** run-paper does **not** accept the EULA itself. Pass `-Dcom.mojang.eula.agree=true`, which Paper reads (`paper-server/patches/sources/net/minecraft/server/Main.java.patch`, "Paper start - eula system property").
- It is a `JavaExec`, so `javaLauncher` works. Use a Java 21 launcher for 1.21.11 and Java 25 for 26.x.

Current Modrinth versions for 26.3 / Paper, from `api.modrinth.com/v2/project/<slug>/version?loaders=["paper"]&game_versions=["26.3"]`:

| Project | Modrinth id | Version (id) | Loaders | Game versions incl. |
|---|---|---|---|---|
| CraftEngine (`craftengine`, GPL-3.0) | tRX6FMfQ | 26.9.2 (`mwFiNxVO`), 2026-09-28 | folia, paper, purpur | 1.21.x … 26.3 |
| BetterModel (`bettermodel`, MIT) | 4h8rX3rt | 3.5.1 (`RZkXlsnV`), 2026-10-15 | folia, paper, purpur | 1.21.x … 26.3 |
| BetterHud (`betterhud2`, MIT) | JUl6WIK2 | 2.2.0-SNAPSHOT-449 (`VHVKage9`), 2026-10-06 | bukkit, folia, paper… | 1.21.x … 26.3 |
| PacketEvents (`packetevents`, GPL-3.0) | HYKaKraK | 2.14.0+spigot (`m78nFxYg`) | bukkit, folia, paper… | 1.21.x … 26.3 |

All four list Folia as a loader.

Licensing: CraftEngine and PacketEvents are GPL-3.0-only. EUPL-1.2 lists GPL-3.0 as a compatible licence, but whether that covers our use is a legal question and **UNVERIFIED**. Because these are separate plugins (compileOnly, not shaded), the question mostly concerns distribution.

```kotlin
plugins { id("xyz.jpenilla.run-paper") version "3.1.0" }

runPaper.folia.registerTask()

tasks {
  runServer {
    minecraftVersion("26.3")
    jvmArgs("-Xmx4G", "-Dcom.mojang.eula.agree=true")
    downloadPlugins {
      modrinth("packetevents", "m78nFxYg")
      modrinth("craftengine", "mwFiNxVO")
      modrinth("bettermodel", "RZkXlsnV")
      modrinth("betterhud2", "VHVKage9")
    }
  }
  named<xyz.jpenilla.runpaper.task.RunServer>("runFolia") {
    minecraftVersion("26.3")      // Folia 26.3 is ALPHA; 1.21.11 is the last STABLE Folia
  }
  register<xyz.jpenilla.runpaper.task.RunServer>("runPaper12111") {
    minecraftVersion("1.21.11")
    pluginJars.from(shadowJar.flatMap { it.archiveFile })
    runDirectory = layout.projectDirectory.dir("run-1.21.11")
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
  }
}
```

### 2.7 Reference multi-module layout

```
settings.gradle.kts         // foojay-resolver-convention 1.0.0
build-logic/                // convention plugins (java toolchain 25, release 21 default)
tfp-core/                   // pure Java, no Bukkit: climate, rock layers, food decay, recipes… (release 21)
tfp-worldgen/               // pure Java noise/biome/rock generation, thread-safe, JMH + jcstress (release 21)
tfp-paper/                  // compileOnly paper-api 1.21.11-R0.1-SNAPSHOT (release 21), paper-plugin.yml
tfp-nms-v1_21_11/           // optional: userdev, paperDevBundle("1.21.11-R0.1-SNAPSHOT"), release 21
tfp-nms-v26_3/              // optional: userdev, paperDevBundle("26.3.build.194-stable"), release 25
tfp-dist/                   // shadow: shades core/worldgen/paper/nms-*, manifest paperweight-mappings-namespace=mojang
tfp-itest/                  // self-test plugin and/or test harness, run via run-paper in CI
```

Root `build.gradle.kts`: `id("io.papermc.paperweight.userdev") version "2.0.0-beta.24" apply false`. Subprojects apply it **without** a version (paperweight-test-plugin README).

---

## 3. Paper APIs relevant to TFC

### 3.1 ChunkGenerator, BiomeProvider, BlockPopulator, LimitedRegion

Paper repo `paper-api/src/main/java/org/bukkit/generator/*.java`, main @ 39887673, 2026-10-15.

**ChunkGenerator**
- Generation runs in this order: `generateNoise → generateSurface → generateBedrock → generateCaves`.
- "Every method listed above as well as `getBaseHeight(...)` **must** be completely thread safe and able to handle multiple concurrent callers" (`ChunkGenerator.java` lines 30–41).
- `isParallelCapable()` is `@deprecated the chunk generation code should be thread safe` (line 365).
- Vanilla steps can be kept with `shouldGenerateNoise/Surface/Caves/Decorations/Mobs/Structures`, each with a `(WorldInfo, Random, chunkX, chunkZ)` overload.
- `shouldGenerateBedrock()` is deprecated: "bedrock generation is part of the surface step".

**BiomeProvider**
- `getBiome(WorldInfo, x, y, z)` and the `getBiome(..., BiomeParameterPoint)` overload "**must** be completely thread safe".
- It should only return biomes listed in `getBiomes(WorldInfo)` and must never return `Biome.CUSTOM` (`BiomeProvider.java` lines 10–81).
- `WorldInfo#vanillaBiomeProvider()` exists (`WorldInfo.java:74`).
- `Biome` is an `OldEnum`/`Keyed` interface in both 1.21.11 (`javap`) and 26.3 (`Biome.java:26`). Custom datapack biomes can therefore be looked up through `RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME)` and returned. Whether a datapack biome is accepted by the BiomeProvider path end to end is **UNVERIFIED** and should be tested early.

**BlockPopulator**
- `populate(WorldInfo, Random, chunkX, chunkZ, LimitedRegion)` "must be completely thread safe" (`BlockPopulator.java:53–69`).
- The legacy `populate(World, Random, Chunk)` overload exists but should be avoided.

**LimitedRegion**
- Gives access to the central chunk plus `getBuffer()` blocks around it. The Javadoc example: "If the method returns 16 you have a working area of 48x48".
- Coordinates are absolute.
- "The buffer zone may or may not be already populated" (`LimitedRegion.java`).

Which threads call the generator on Paper and Folia is **UNVERIFIED** from docs. The Javadoc only mandates thread safety. Assume arbitrary chunk-system worker threads, and never touch `World`/`Chunk` API from generator code.

### 3.2 Async chunk APIs and snapshots

- `World#getChunkAtAsync(...)` returns `CompletableFuture<Chunk>`, with `Location`, `Block`, `gen` and `urgent` variants (`World.java` ~1475–1534).
- `Chunk#getChunkSnapshot(includeMaxBlockY, includeBiome, includeBiomeTempRain, includeLightData)` (`Chunk.java:124`).
- A `ChunkSnapshot` is immutable. That supports the "snapshot → async compute → region-thread commit" design: take the snapshot on the owning thread, compute on an executor, then commit through `RegionScheduler.execute(plugin, world, cx, cz, ...)`.
- `Entity#teleportAsync(Location[, cause])` returns `CompletableFuture<Boolean>` (`Entity.java:273–289`).

### 3.3 Lifecycle API, registries, datapack discovery (custom biomes)

**Lifecycle API** (https://docs.papermc.io/paper/dev/lifecycle/)
- Get the manager with `context.getLifecycleManager()` in the bootstrapper or `getLifecycleManager()` in the plugin.
- Handlers take `.priority(int)` or `.monitor()`.
- `LifecycleEvents` contains only `COMMANDS`, `TAGS` and `DATAPACK_DISCOVERY`, in both 26.3 (source) and 1.21.11 (`javap`).

**Custom biomes go through datapack discovery.** There is **no `RegistryEvents.BIOME`**. The writable registries in 26.3 are:
- GAME_EVENT, TRIM_MATERIAL, TRIM_PATTERN, DAMAGE_TYPE
- WOLF/CAT/FROG/CHICKEN/COW/PIG/ZOMBIE_NAUTILUS variants
- ENCHANTMENT, JUKEBOX_SONG, BANNER_PATTERN, DECORATED_POT_PATTERN, PAINTING_VARIANT, INSTRUMENT
- SULFUR_CUBE_ARCHETYPE, DIALOG

(Paper repo `paper-api/.../registry/event/RegistryEvents.java` lines 52–70.) Ship biomes, and the dimension type and noise settings if needed, as a datapack inside the jar:

```java
public final class TfpBootstrap implements PluginBootstrap {
  @Override public void bootstrap(BootstrapContext ctx) {
    ctx.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
      try {
        URI uri = getClass().getResource("/tfc_datapack").toURI();
        event.registrar().discoverPack(uri, "provided");   // pack id becomes "<PluginName>/provided"
      } catch (URISyntaxException | IOException e) { throw new RuntimeException(e); }
    });
  }
}
```

- Source: https://docs.papermc.io/paper/dev/lifecycle/datapacks/. The page says "The datapack discovery API is currently experimental".
- Ship the source files under `src/main/resources/tfc_datapack/`, not a zip.
- Check the result with `/datapack list enabled`.
- The registry API is also marked experimental (https://docs.papermc.io/paper/dev/registries/).
- The 26.3 post says many experimental APIs, including most of the data component API, are no longer experimental (https://papermc.io/news/26-3).

**Seasonal grass colours by rewriting biome IDs in chunk packets.** The client only knows biomes that were sent in the registry during the configuration phase. Every "seasonal variant" biome therefore has to be a real registered biome from the datapack above, and its numeric network ID has to be looked up at runtime.

How the network ID is obtained without NMS is **UNVERIFIED**. PacketEvents exposes biome palettes (`Chunk_v1_18` `biomeData`, `api/.../world/chunk/impl/v_1_18/Chunk_v1_18.java:46`). The registry-to-ID mapping may come from PacketEvents' synchronized-registry tracking or from NMS. Investigate this.

### 3.4 PersistentDataContainer on chunks

- `public interface Chunk extends PersistentDataHolder` (`paper-api/.../org/bukkit/Chunk.java:23`).
- On Folia, read and write it only on the region that owns the chunk (§4).
- The 26.2 post notes that beds are no longer block entities and lose their PDC. Data must be migrated through `AsyncServerDataFixerRemoveBlockEntityEvent` (https://papermc.io/news/26-2). This matters for TFC block-entity designs, because a block entity can be removed by a vanilla update.

### 3.5 Brigadier command API

- Docs: https://docs.papermc.io/paper/dev/command-api/basics/registration/.
- Register through `LifecycleEvents.COMMANDS`, preferably in the bootstrapper ("This requires you to use a Paper plugin"), with `event.registrar().register(Commands.literal("tfc")...build())`. `BasicCommand` is also supported.
- Commands are re-registered automatically on reload.
- On Folia, player commands run on the player's region and console commands on the global region (Folia README, "Thread contexts for API").

### 3.6 Mob Goals API

`com.destroystokyo.paper.entity.ai.{MobGoals, Goal, GoalKey, GoalType}` are present in both 26.3 (source) and the 1.21.11 API jar. Goals tick on the entity's owning thread. On Folia, all entity work belongs on `entity.getScheduler()`.

### 3.7 Adventure

| Server | Adventure version | Source |
|---|---|---|
| Paper 26.3 | `net.kyori:adventure-api:5.2.0` | `META-INF/libraries.list` of paper-26.3-194.jar |
| Paper 1.21.11 | 4.26.1 | paper-1.21.11-132.jar |

- Do not shade Adventure.
- See §1.5 for 4 → 5 compatibility: sealed types, typed `ClickEvent`, removed deprecated APIs, and the 5.2.0 binary-compat bridges (https://docs.papermc.io/adventure/migration/adventure-4.x/).

### 3.8 Packet-level access

Paper has no official packet API. There are three options.

| Option | Pros | Cons |
|---|---|---|
| **PacketEvents** (`packetevents-spigot` 2.14.0, compileOnly from `https://repo.codemc.io/repository/maven-releases/`) | Version-independent wrappers including chunk data and biome palettes; supports 1.21–26.3; `folia-supported: true` in its plugin.yml (`spigot/src/main/resources/plugin.yml:6`); avoids NMS | Extra runtime plugin (GPL-3.0); wrappers lag new MC versions; listener thread not documented (see below) |
| **Netty injection through Paper's internal `ChannelInitializeListenerHolder.addListener(Key, ChannelInitializeListener)`** | No third-party dependency; called after each channel is initialised (`ServerConnectionListener.java.patch`: `ChannelInitializeListenerHolder.callListeners(channel)`) | Javadoc says "This is not officially supported API and we make no guarantees to the existence or state of this class" (`paper-server/src/main/java/io/papermc/paper/network/ChannelInitializeListenerHolder.java`); you still need NMS packet classes, so per-version modules |
| **NMS packet classes directly** (with paperweight userdev) | Full control, no extra plugin | Per-version code; on 1.21.11 relies on Mojang-mapped runtime; highest maintenance |

Which thread outgoing handlers run on: in Paper's `Connection#sendPacket`, the packet is written directly if `this.channel.eventLoop().inEventLoop()`, and otherwise scheduled with `channel.eventLoop().execute(...)` (`paper-server/patches/sources/net/minecraft/network/Connection.java.patch` ~lines 218–245). Outbound pipeline handlers, and therefore PacketEvents send listeners and our chunk and lore rewriting, run on the **Netty event-loop thread of that connection**.

Consequences:
- Never call Bukkit world or entity API from a packet handler.
- Use only immutable or concurrent caches, such as the season state or a per-player item-lore cache.
- Keep handlers allocation-light. Chunk packets are large, and rewriting biome palettes must be fast.

PacketEvents' own docs do not state the listener thread (https://docs.packetevents.com/processing-and-sending/master/). That it is the Netty thread is inferred from the above and is **UNVERIFIED** for PacketEvents specifically.

---

## 4. Folia

### 4.1 Threading model

Sources: Folia README at https://github.com/PaperMC/Folia (branch `ver/26.3.x`, commit 597e4137, 2026-10-14) and https://docs.papermc.io/folia/reference/overview/.

- Loaded chunks are grouped into independent regions. Each region has its own 20 TPS tick loop on a shared pool. "There is no main thread anymore."
- Adjacent regions must merge, and a ticking region may not grow.
- The **global region** is a single 20 TPS task. It handles "game rules, global game time, daylight time, console command handling, world border, weather, and others".
- Region data (entity, chunk and POI) "may only be accessed while ticking the region and by the thread ticking the region".
- Events and commands run in **parallel** on different regions. Plugin-held state needs ordinary concurrency control: "a concurrent collection used carelessly will only _hide_ threading issues".
- As a rule of thumb, a region owns roughly 8 chunks around the source of an event. This is not guaranteed, so use `isOwnedByCurrentRegion`.
- Events about one entity fire on the region that owns it. Events that act on a target entity fire on the target's region. The `async` modifier on events is deprecated in Folia.

### 4.2 Scheduler API

Signatures verbatim from the Paper repo `paper-api/src/main/java/io/papermc/paper/threadedregions/scheduler/*.java`. The same classes exist in the 1.21.11 API jar, so they are usable on plain Paper too.

```java
// Server: getRegionScheduler(), getAsyncScheduler(), getGlobalRegionScheduler(); Entity: getScheduler()

interface RegionScheduler {
  void execute(Plugin plugin, World world, int chunkX, int chunkZ, Runnable run);
  default void execute(Plugin plugin, Location location, Runnable run);
  ScheduledTask run(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task);
  ScheduledTask runDelayed(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task, long delayTicks);
  ScheduledTask runAtFixedRate(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task,
                               long initialDelayTicks, long periodTicks);
  // + Location overloads
}

interface EntityScheduler {
  boolean execute(Plugin plugin, Runnable run, @Nullable Runnable retired, long delay);
  @Nullable ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task, @Nullable Runnable retired);
  @Nullable ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, @Nullable Runnable retired, long delayTicks);
  @Nullable ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, @Nullable Runnable retired,
                                         long initialDelayTicks, long periodTicks);
}  // null/false => entity removed ("retired"); retired callback must not load chunks/remove entities etc.

interface GlobalRegionScheduler {
  void execute(Plugin plugin, Runnable run);
  ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task);
  ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delayTicks);
  ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks);
  void cancelTasks(Plugin plugin);
}

interface AsyncScheduler {
  ScheduledTask runNow(Plugin plugin, Consumer<ScheduledTask> task);
  ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delay, TimeUnit unit);
  ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, long initialDelay, long period, TimeUnit unit);
  void cancelTasks(Plugin plugin);
}

interface ScheduledTask { Plugin getOwningPlugin(); boolean isRepeatingTask(); CancelledState cancel();
                          ExecutionState getExecutionState(); default boolean isCancelled(); }
```

Ownership checks on `Server`/`Bukkit` (`Server.java` 2709–2800):
- `isOwnedByCurrentRegion(World, Position[, int squareRadiusChunks])`
- `(Location[, int])`
- `(Block)`
- `(World, int chunkX, int chunkZ[, int radius])`
- `(World, minCX, minCZ, maxCX, maxCZ)`
- `(Entity)`
- `isGlobalTickThread()`

Paper's docs say these schedulers can be used on Paper: "they will be internally handled" (https://docs.papermc.io/paper/dev/folia-support/). Do **not** use the region scheduler for entity work, because entities move between regions.

### 4.3 Broken or unsupported on Folia

From the Folia README "Current broken API", and the `UnsupportedOperationException` throws in `folia-server/paper-patches/features/0001-Region-Threading-Base.patch`:

- **BukkitScheduler.** `CraftScheduler#handle` throws `UnsupportedOperationException` (patch ~line 3967).
- **All scoreboard API.** `CraftScoreboard#registerNewObjective` throws. This is relevant if BetterHud or our HUD uses scoreboards; their Folia support is claimed on Modrinth.
- **World loading and unloading.** `CraftServer#createWorld` and `unloadWorld` throw. Custom TFC worlds must therefore be the server's configured default worlds, with the generator set via `bukkit.yml` `worlds.<name>.generator` or via `getDefaultWorldGenerator`. You cannot call `createWorld` at runtime.
- **`Entity#teleport`.** "This will NEVER UNDER ANY CIRCUMSTANCE come back, use teleportAsync". The patch throws "Must use teleportAsync while in region threading".
- Portal, respawn and some login API are broken.
- Off-region `dispatchCommand` throws.
- `MCUtil.ensureMain` throws off-thread.
- Planned: "Super aggressive thread checks across the board."

### 4.4 Writing code that runs on both Paper and Folia

1. Use the four Folia schedulers everywhere. They work on Paper. Never use `Bukkit.getScheduler()`.
2. Detect Folia only when behaviour must differ:
   ```java
   static final boolean FOLIA = ServerBuildInfo.buildInfo().isBrandCompatible(Key.key("papermc", "folia"));
   ```
   This comes from https://docs.papermc.io/paper/dev/folia-support/. `ServerBuildInfo` is in the 1.21.11 API.
3. Use `teleportAsync` only.
4. Keep global state (calendar/season, climate) behind thread-safe structures. Mutate it from the global region scheduler or with atomics, and publish immutable snapshots that region threads and Netty threads read.
5. Before touching a block, chunk or entity, assert `Bukkit.isOwnedByCurrentRegion(...)` in debug builds, and route the work through `RegionScheduler` or `EntityScheduler` if the check fails.
6. Do not use the scoreboard API, `createWorld`, or the sync `teleport`.
7. Add `folia-supported: true`.

`Bukkit.isPrimaryThread()` on Paper returns `TickThread.isTickThread()` (Paper repo `paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java:2299–2300`). On Folia it therefore presumably returns true on any region or global tick thread, which is **UNVERIFIED**. Use it only as "am I on *a* tick thread". For "am I on the *right* thread", use `isOwnedByCurrentRegion`.

---

## 5. Testing

### 5.1 Plain unit tests (most coverage)

- Keep `tfp-core` and `tfp-worldgen` free of Bukkit, with pure Java and deterministic seeds. Test them with JUnit Jupiter 6.1.3. MockBukkit itself now depends on `junit-jupiter-api:6.1.3` (MockBukkit `build.gradle.kts:30`).
- Property-based tests (jqwik 1.10.2) suit noise, climate and rock-layer invariants.

### 5.2 MockBukkit

Source: https://github.com/MockBukkit/MockBukkit, default branch `minecraft/v26.2`, last commit 2026-10-06.

- **Coordinates:** `testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.117.0")`. The 1.21 line is `mockbukkit-v1.21:4.116.3`. There is **no `mockbukkit-v26.1` or `v26.3` artifact yet**; branch `upgrade/v26.3` exists. The artifact name is `mockbukkit-v${paper.api.version}` (`build.gradle.kts:180`).
- **Pinned API:** `gradle.properties` sets `paper.api.full-version=26.2.build.111-stable` and `java.version=25`. The README suggests reading `Paper-Version` from the MockBukkit jar manifest so that the test classpath uses the matching paper-api.
- **What it supports:**
  - Folia schedulers.
  - `isOwnedByCurrentRegion`, but **every overload just returns `isPrimaryThread()`** (`ServerMock.java` ~2818–2860), so it cannot catch wrong-region bugs.
  - Lifecycle manager mock (`LifecycleEventManagerMock`).
  - Reading `paper-plugin.yml` through `PluginManagerMock`, parsed as a `PluginDescriptionFile`. Bootstrapper and loader are not executed; this is inferred from the code and **UNVERIFIED**.
  - `MockBukkitExtension` with `@MockBukkitInject` for JUnit.
  - `AsyncCatcher`.
- **Limitations relevant to TFC:**
  - `WorldMock#getGenerator()` throws `UnimplementedOperationException` (`WorldMock.java:1363`), and `WorldMock` has 107 `UnimplementedOperationException` sites. So there is no worldgen pipeline.
  - No NMS.
  - No network or packets.
  - No real chunk system or threading.
  - `ChunkDataMock` exists, so `ChunkGenerator.generateNoise(...)` can be unit-tested by calling it directly with a `ChunkData` from `server.createChunkData(world)` (`ServerMock.java:2163`).
- **Use it for:** event listeners, commands, PDC logic, item and lore building, and scheduler wiring.

### 5.3 Integration tests on a real server

- **run-paper headless in CI.** `runServer` with `-Dcom.mojang.eula.agree=true` and `--nogui`, which run-paper adds. Watchdog is disabled. Download the dependency plugins with `downloadPlugins`.
- **Self-test plugin pattern.** This is a recommendation; no official PaperMC harness exists (UNVERIFIED that none exists).
  - A `tfp-itest` plugin, or the main plugin with `-Dtfp.selftest=true`, schedules test cases after `ServerLoadEvent`.
  - Example cases: generate chunks via `getChunkAtAsync(x, z, true)`, then assert block and biome distributions; place blocks and tick them; spawn entities and check goals; run commands with `dispatchCommand` from the console on the global region.
  - It writes JUnit-XML or a JSON report to the run directory, then calls `Bukkit.shutdown()`. The exit code or report decides pass or fail.
  - Run the same suite under `runFolia` to catch threading violations, since Folia throws on wrong-thread access.
- **GitHub Actions sketch:**

```yaml
jobs:
  itest:
    strategy: { matrix: { mc: ["1.21.11", "26.3"], platform: [paper, folia] } }
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: |
          21
          25 }
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew :tfp-itest:runSelfTest -Pmc=${{ matrix.mc }} -Pplatform=${{ matrix.platform }}
        timeout-minutes: 20
      - uses: actions/upload-artifact@v7
        if: always()
        with: { name: itest-${{ matrix.mc }}-${{ matrix.platform }}, path: "**/run-*/logs/**" }
```

Here `runSelfTest` is a custom `RunServer` task with `minecraftVersion(mc)`, the EULA property and `-Dtfp.selftest=true`. The action major versions are the latest tags as of 2026-10-16.

### 5.4 Concurrency testing

- **jcstress 0.16** (`org.openjdk.jcstress:jcstress-core`) for lock-free and racy structures in core: season/climate snapshot publication and any JCTools queues. Gradle plugin `io.github.reyerizo.gradle.jcstress` 1.0.0 (usage **UNVERIFIED**).
- **Runtime assertions:**

```java
static void assertOwned(Location l) {
  if (!Bukkit.isOwnedByCurrentRegion(l)) throw new IllegalStateException("not owner: " + Thread.currentThread().getName());
}
static void assertNotTickThread() { if (Bukkit.isPrimaryThread()) throw new IllegalStateException("blocking on tick thread"); }
```

  Enable them through a system property in tests and in run-paper.
- **Awaitility 4.3.0** for async assertions in integration tests.
- Run the integration suite on Folia. Folia's thread checks are the strongest available detector.

### 5.5 Profiling

- **spark is bundled.** "Starting with 1.21, Paper bundles the spark profiler" (https://docs.papermc.io/paper/profiling/). Paper 26.3 bundles `me.lucko:spark-paper:1.10.191` and 1.21.11 bundles `1.10.152` (`libraries.list`).
- **Timings** is "deprecated in favor of spark and turned off by default in 1.21". `co.aikar.timings.Timings` is `@Deprecated(forRemoval = true)` with the comment "Timings will be removed in the future" (Paper repo `paper-api/.../co/aikar/timings/Timings.java:43–46`). Do not use it.
- Usage: `/spark profiler start --timeout 600` (profiling docs). Also use the `--thread` options to include chunk-worker and Netty threads (flag **UNVERIFIED** on that page).

### 5.6 Worldgen benchmarking

- Use JMH 1.37 through `me.champeau.jmh` 0.7.3 in `tfp-worldgen`. Benchmark the pure-Java pipeline per chunk (noise, rock layers, surface, biome sampling) with `@Threads(N)` to reflect concurrent callers.
- In-server, combine pre-generation timing with chunk-generation spark profiles. A Chunky-style pregen plugin through `downloadPlugins` is an option, **UNVERIFIED** on 26.x.

---

## 6. Libraries and what Paper already bundles

Bundled in Paper 26.3 #194 (`META-INF/libraries.list` of `paper-26.3-194.jar`), on the plugin classpath at runtime:

| Library | Paper 26.3 | Paper 1.21.11 | Notes |
|---|---|---|---|
| fastutil | **8.5.18** | 8.5.18 | It is an `api(...)` dependency of paper-api (`paper-api/build.gradle.kts:61`), so it is on the compile classpath through paper-api. **Do not shade.** |
| Guava | 33.6.0-jre | 33.5.0-jre | api dependency |
| Gson | 2.14.0 | 2.13.2 | api dependency |
| Adventure | 5.2.0 | 4.26.1 | §1.5 |
| Brigadier | 1.3.11 | – | api dependency |
| JOML | 1.10.9 | – | api dependency |
| SnakeYAML / Configurate | 2.2 / 4.2.0 | – | |
| Netty | 4.2.16.Final | 4.2.7.Final | |
| concurrentutil | `ca.spottedleaf.leafpile:concurrentutil:1.2.4` | `ca.spottedleaf:concurrentutil:0.0.8` | internal; do not depend on it |
| spark | spark-paper 1.10.191, spark-api | 1.10.152 | |
| LZ4, DFU, authlib, sqlite-jdbc, mysql-connector-j, disruptor 3.4.4 | yes | – | sqlite and mysql are runtime-only |
| **Caffeine** | **not bundled** | not bundled | shade and relocate, or use the library loader |
| **JCTools** | **not bundled** | not bundled | shade and relocate, or use the library loader |

Recommendations:
- **fastutil:** use `compileOnly` and rely on the server copy. Version 8.5.18 is on both target lines. Shading a second copy without relocation causes conflicts, and relocating it only bloats the jar.
- **Caffeine 3.3.1 and JCTools 4.0.7:** either list them in `PluginLoader` through `MavenLibraryResolver` with `MAVEN_CENTRAL_DEFAULT_MIRROR`, which needs no shading but downloads on first start, or shade them with `relocate("com.github.benmanes.caffeine", "dev.tfp.lib.caffeine")` and `relocate("org.jctools", "dev.tfp.lib.jctools")`. Other plugins may ship different versions, but Paper-plugin classloader isolation reduces that risk.
- The paper-plugin docs list the library types as `JarLibrary` and `MavenLibraryResolver` (https://docs.papermc.io/paper/dev/getting-started/paper-plugins/).

---

## 7. Recommendations summary

1. **Targets.**
   - Primary: **Paper 26.3**, with 26.2 while it is supported, both Java 25.
   - Secondary: **Paper/Folia 1.21.11** (Java 21), which is now unsupported upstream. Decide whether it is worth the dual-Java and dual-Adventure complexity.
   - Folia 26.3 is ALPHA, so treat Folia 26.x as best-effort for now.
2. **No NMS in the core path.** Use the Paper API, which is common to 1.21.11 and 26.x, plus PacketEvents for packets. Isolate any unavoidable NMS in `nms-v1_21_11` and `nms-v26_3` modules as in PaperMC's multi-project example. On 26.x there is no reobf and no remap.
3. **Use `paper-plugin.yml`** for the bootstrapper (datapack discovery of biomes, Brigadier commands), the loader (Caffeine/JCTools), `load: STARTUP`, `folia-supported: true`, and ordered dependencies on CraftEngine, BetterModel, BetterHud and PacketEvents.
4. **Use Folia schedulers everywhere.** Use `teleportAsync`, no scoreboard, and no runtime `createWorld`. Read global season state from immutable snapshots on region and Netty threads.
5. **Testing pyramid:**
   - Pure-Java unit, property and jcstress tests in core and worldgen.
   - JMH for worldgen.
   - MockBukkit for listeners, commands and PDC (no worldgen, region checks not meaningful).
   - A run-paper self-test matrix (Paper/Folia × 1.21.11/26.3) in GitHub Actions.
   - spark for profiling.
6. **Pin exact builds in CI** (`26.3.build.194-stable`) and use `+` only for local dev.

---

## 8. UNVERIFIED / open questions

1. Whether Mojang-mapped names on 1.21.11 equal official names on 26.x for the NMS classes we would touch. Expected yes, but not checked.
2. Exact `api-version` string semantics for a single jar (`'1.21.11'` vs `'1.21'`), and whether commodore/compat rewrites applied to old api-versions have side effects on 26.x.
3. Whether a datapack-registered custom biome can be returned from `BiomeProvider#getBiome` and saved correctly on both 1.21.11 and 26.x. Needs a spike.
4. How to obtain biome **network IDs** for packet rewriting without NMS (PacketEvents registry sync vs NMS registry access).
5. Thread on which PacketEvents listeners fire. Inferred to be the Netty event loop from Paper's `Connection` patch; not stated in PacketEvents docs.
6. Which thread pool calls `ChunkGenerator` and `BlockPopulator` on Paper and Folia (worker threads assumed). Whether `BlockPopulator` runs on a region thread under Folia.
7. `Bukkit.isPrimaryThread()` semantics on Folia (presumably true on any tick thread).
8. MockBukkit does not run `PluginBootstrap`/`PluginLoader`, so a bootstrapper-dependent plugin needs a test seam. Inferred from code.
9. No `mockbukkit-v26.3` release yet (branch `upgrade/v26.3` exists). No MockBukkit for 26.1.
10. Plugin names (`name:`) of CraftEngine, BetterModel, BetterHud and PacketEvents for `paper-plugin.yml` dependencies. Also whether BetterHud's scoreboard usage is Folia-safe; it claims Folia support.
11. Licence compatibility of EUPL-1.2 with GPL-3.0-only dependency plugins, for distribution or bundling in a modpack. This is a legal question.
12. The minecraft.net obfuscation article could not be fetched (HTTP 503). The claim rests on Mojang manifests showing no mappings for 26.x and on Paper docs and news.
13. Whether `/spark profiler --thread *` is the current flag syntax.
14. Usage details of the `io.github.reyerizo.gradle.jcstress` plugin (only the plugin-portal metadata was checked).
15. Folia 26.3 stability timeline (currently ALPHA build #3).

---

## 9. Source index

**Primary data endpoints (fetched 2026-10-16)**
- Mojang manifest: https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
- Fill API: https://fill.papermc.io/v3/projects/paper, https://fill.papermc.io/v3/projects/folia (+ `/versions/<v>`, `/builds/latest`)
- Paper Maven: https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml, `.../io/papermc/paper/dev-bundle/...`, `.../dev/folia/folia-api/...`, `.../dev/folia/dev-bundle/...`
- Gradle Plugin Portal metadata for paperweight-userdev, run-paper, shadow, resource-factory, jmh, jcstress plugin
- Maven Central (Google mirror) metadata for mockbukkit, caffeine, fastutil, jctools, jmh, jcstress, junit, awaitility
- CodeMC: https://repo.codemc.io/repository/maven-releases/com/github/retrooper/packetevents-spigot/maven-metadata.xml
- Modrinth API: https://api.modrinth.com/v2/project/{craftengine,bettermodel,betterhud2,packetevents}
- Paper server jars `paper-26.3-194.jar` and `paper-1.21.11-132.jar` (`META-INF/libraries.list`) from fill-data.papermc.io

**Docs**
- https://docs.papermc.io/paper/dev/project-setup/
- https://docs.papermc.io/paper/dev/userdev/
- https://docs.papermc.io/paper/dev/getting-started/paper-plugins/
- https://docs.papermc.io/paper/dev/lifecycle/
- https://docs.papermc.io/paper/dev/lifecycle/datapacks/
- https://docs.papermc.io/paper/dev/registries/
- https://docs.papermc.io/paper/dev/command-api/basics/registration/
- https://docs.papermc.io/paper/dev/folia-support/
- https://docs.papermc.io/paper/dev/scheduler/
- https://docs.papermc.io/paper/profiling/
- https://docs.papermc.io/folia/reference/overview/
- https://docs.papermc.io/adventure/migration/adventure-4.x/
- https://gradleup.com/shadow/, https://gradleup.com/shadow/getting-started/, https://gradleup.com/shadow/configuration/relocation/
- https://docs.packetevents.com/introduction/development-setup/, https://docs.packetevents.com/processing-and-sending/master/

**News and releases**
- https://papermc.io/news/26-1, https://papermc.io/news/26-2, https://papermc.io/news/26-3
- https://papermc.io/news/important-dev-psa-future-removal-of-cb-package-relocation
- https://github.com/PaperMC/adventure/releases/tag/v5.2.0
- https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition (503 at fetch time)

**Repositories (shallow clones, 2026-10-16)**
- PaperMC/Paper `main` @ 39887673 (2026-10-15):
  - `gradle.properties` (mcVersion=26.3)
  - `paper-api/src/main/java/org/bukkit/generator/{ChunkGenerator,BiomeProvider,BlockPopulator,LimitedRegion,WorldInfo}.java`
  - `.../io/papermc/paper/threadedregions/scheduler/*.java`
  - `.../org/bukkit/Server.java`, `.../org/bukkit/Chunk.java`, `.../org/bukkit/World.java`, `.../org/bukkit/entity/Entity.java`
  - `.../io/papermc/paper/registry/event/RegistryEvents.java`
  - `.../io/papermc/paper/plugin/loader/library/impl/MavenLibraryResolver.java`
  - `.../io/papermc/paper/ServerBuildInfo.java`
  - `.../co/aikar/timings/Timings.java`
  - `paper-api/build.gradle.kts`, `paper-server/build.gradle.kts`
  - `paper-server/src/main/java/io/papermc/paper/network/ChannelInitializeListener{,Holder}.java`
  - `paper-server/src/main/java/io/papermc/paper/plugin/provider/configuration/PaperPluginMeta.java`
  - `paper-server/src/main/java/org/bukkit/craftbukkit/{CraftServer,util/CraftMagicNumbers}.java`
  - `paper-server/patches/sources/net/minecraft/network/Connection.java.patch`
  - `paper-server/patches/sources/net/minecraft/server/network/ServerConnectionListener.java.patch`
  - `paper-server/patches/sources/net/minecraft/server/Main.java.patch`
- PaperMC/Folia `ver/26.3.x` @ 597e4137 (2026-10-14):
  - `README.md`
  - `folia-server/paper-patches/features/0001-Region-Threading-Base.patch`
  - `folia-api/paper-patches/features/0003-Require-plugins-to-be-explicitly-marked-as-Folia-sup.patch`
- PaperMC/paperweight-test-plugin `master` and `multi-project` (2026-04-15)
- jpenilla/run-task `master` (2026-08-08):
  - `README.md`
  - `plugin/src/main/kotlin/xyz/jpenilla/runpaper/{RunPaperExtension,RunPaperPlugin}.kt`
  - `.../runpaper/task/RunServer.kt`
  - `.../runtask/pluginsapi/{DownloadPluginsSpec,PluginDownloadServiceImpl}.kt`
  - `.../runtask/service/DownloadsAPIService.kt`
- MockBukkit/MockBukkit `minecraft/v26.2` (2026-10-06):
  - `README.md`, `gradle.properties`, `build.gradle.kts`
  - `src/main/java/org/mockbukkit/mockbukkit/{ServerMock,MockBukkitExtension}.java`
  - `.../world/WorldMock.java`
  - `.../plugin/PluginManagerMock.java`
- retrooper/packetevents (2026-10-15):
  - `CHANGELOG.md`
  - `api/.../manager/server/ServerVersion.java`
  - `api/.../protocol/world/chunk/impl/v_1_18/Chunk_v1_18.java`
  - `spigot/src/main/resources/plugin.yml`
