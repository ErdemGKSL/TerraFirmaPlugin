# Self-test harness and CI snippets

Starting points, from `paper-folia-threading/references/tooling-report.md` §2.6, §5.3. Versions were current on 2026-10-16. Re-check them before pinning, and pin exact builds in CI.

## run-paper with the client plugins

```kotlin
plugins { id("xyz.jpenilla.run-paper") version "3.1.0" }

runPaper.folia.registerTask()      // adds runFolia

val java25 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }

tasks {
  runServer {
    minecraftVersion("26.3")
    javaLauncher = java25
    jvmArgs("-Xmx4G", "-Dcom.mojang.eula.agree=true", "-Dtfp.assertThreads=true")
    downloadPlugins {
      // Modrinth project slug + version id (looked up 2026-10-16; update as needed)
      modrinth("craftengine", "mwFiNxVO")    // 26.9.2
      modrinth("bettermodel", "RZkXlsnV")    // 3.5.1
      modrinth("betterhud2",  "VHVKage9")    // 2.2.0-SNAPSHOT-449 (2.0.0 if you need a release)
      // modrinth("packetevents", "m78nFxYg") // 2.14.0, if adopted
    }
  }
  named<xyz.jpenilla.runpaper.task.RunServer>("runFolia") {
    minecraftVersion("26.3")          // Folia 26.3 is ALPHA; 1.21.11 is the last stable Folia
    javaLauncher = java25
  }
  register<xyz.jpenilla.runpaper.task.RunServer>("runSelfTest") {
    minecraftVersion(providers.gradleProperty("mc").getOrElse("26.3"))
    javaLauncher = java25             // BetterModel/BetterHud need Java 25 on 1.21.11 too
    runDirectory = layout.buildDirectory.dir("selftest-run")
    jvmArgs("-Dcom.mojang.eula.agree=true", "-Dtfp.selftest=true", "-Dtfp.assertThreads=true")
    // pluginJars.from(...) and downloadPlugins as above
  }
}
```

Notes:
- run-paper doesn't accept the EULA by itself. The `-Dcom.mojang.eula.agree=true` property does.
- For Folia, make `runSelfTest` configurable with `-Pplatform=folia`, or register a second task based on `runFolia`.
- BetterHud 2.2 and BetterModel 3.x support MC 1.21.4+ only. The lowest 1.21.x we test is therefore 1.21.11 (the last Paper/Folia stable 1.21 build).

## Self-test plugin shape

```java
public final class SelfTestRunner {
    // Runs after: server load + CraftEngine pack built + BetterModel PluginEndReloadEvent(Success)
    public void runAll() {
        var results = new ArrayList<CaseResult>();
        for (SelfTestCase c : cases) {
            results.add(c.run(ctx).orTimeout(c.timeout().toMillis(), TimeUnit.MILLISECONDS).join());  // join on an async thread, never on a tick thread
        }
        Reports.writeJUnitXml(results, Path.of("selftest-report.xml"));
        Bukkit.getGlobalRegionScheduler().execute(plugin, Bukkit::shutdown);
    }
}
```

- Each case returns a `CompletableFuture<CaseResult>` and does its world work through our `Scheduler` (region/entity) API, so the harness itself respects Folia's rules.
- Drive the harness from an async thread, and never block a tick thread waiting on a case.
- Also fail the run if the server log contains any `IllegalStateException` from thread assertions, or Folia's "not owned by current region" errors.

## GitHub Actions

```yaml
jobs:
  unit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: 25 }
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew check
  itest:
    needs: unit
    strategy:
      fail-fast: false
      matrix:
        mc: ["1.21.11", "26.3"]
        platform: [paper, folia]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: 25 }
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew :itest:runSelfTest -Pmc=${{ matrix.mc }} -Pplatform=${{ matrix.platform }}
        timeout-minutes: 20
      - uses: actions/upload-artifact@v7
        if: always()
        with: { name: itest-${{ matrix.mc }}-${{ matrix.platform }}, path: "**/selftest-run/**/*.xml\n**/selftest-run/logs/**" }
```

## Thread assertions

```java
public final class ThreadAsserts {
    static final boolean ON = Boolean.getBoolean("tfp.assertThreads");
    public static void owned(Location l) {
        if (ON && !Bukkit.isOwnedByCurrentRegion(l))
            throw new IllegalStateException("not region owner: " + Thread.currentThread().getName() + " @ " + l);
    }
    public static void owned(Entity e) {
        if (ON && !Bukkit.isOwnedByCurrentRegion(e))
            throw new IllegalStateException("not entity owner: " + Thread.currentThread().getName());
    }
    public static void notTickThread() {
        if (ON && Bukkit.isPrimaryThread())
            throw new IllegalStateException("blocking work on a tick thread");
    }
}
```

## JMH (worldgen)

```kotlin
plugins { id("me.champeau.jmh") version "0.7.3" }
jmh { threads = 8; fork = 1; warmupIterations = 3; iterations = 5 }
```

Keep benchmarks deterministic: fixed seed, fixed chunk set.
