# M0-ENV-01: Spike harness

**Status:** done (2026-10-16)

## Setup
- Gradle 9.8.1 wrapper in `spikes/` (one throwaway build; each spike is a subproject). Java 25 toolchain (Temurin 25.0.4.1).
- `spikes/harness/`: a tiny plugin (`paper-plugin.yml`: `load: STARTUP`, `folia-supported: true`, `CraftEngine`/`BetterModel`/`BetterHud` required with `load: BEFORE` + `join-classpath: true`, `packetevents` optional). It writes `spike-report.txt` after `ServerLoadEvent` and, with `-Pautostop=<s>`, shuts the server down.
- run-paper 3.1.0 tasks `:harness:run{Paper,Folia}{1_21_11,26_3}`. Folia tasks use `DownloadsAPIService.folia(project)`. EULA via `-Dcom.mojang.eula.agree=true`.
- `downloadPlugins` from Modrinth (pinned version ids): CraftEngine 26.9.2 (`mwFiNxVO`), BetterModel 3.5.1 (`RZkXlsnV`), BetterHud 2.2.0-SNAPSHOT-449 (`VHVKage9`), PacketEvents 2.14.0 (`m78nFxYg`).
- `spikes/run-matrix.sh [autostop] [gradle args]` runs all targets and prints `exit`, the report result and the count of suspicious log lines. Extra spike jars go in with `-PspikeJars=a.jar,b.jar`.

## Network notes (this container, also relevant for CI)
- Maven Central answers **429** to unauthenticated bursts. Both `pluginManagement` and `dependencyResolutionManagement` list `https://repo.papermc.io/repository/maven-public/` first (it proxies Central).
- `repo.momirealms.net` (CE's Maven) was unreachable. Modrinth's Maven endpoint (`maven.modrinth:craftengine:<id>`) serves the plugin jar, which contains the API, so it works for `compileOnly`.
- PacketEvents' update check hits `api.github.com` (403 here). Harmless WARN; disable `check-for-updates` in its config for CI.

## Result
| Target | Build | CE | BM | BH | PE | Errors |
|---|---|---|---|---|---|---|
| Paper 1.21.11 | 1.21.11-132 | 26.9.2 ✓ | 3.5.1 ✓ | 2.2.0-S449 ✓ | 2.14.0 ✓ | 0 |
| Paper 26.3 | 26.3-196 | ✓ | ✓ | ✓ | ✓ | 0 |
| Folia 1.21.11 | 1.21.11-14 | ✓ | ✓ | ✓ | ✓ | 0 |
| Folia 26.3 | 26.3-3 (alpha) | ✓ | ✓ | ✓ | ✓ | 0 |

"Errors" excludes the EULA flag notice (Spigot logs it at ERROR level on 1.21.11) and the PacketEvents update check.

Side results:
- The harness jar is compiled once against `paper-api:1.21.11-R0.1-SNAPSHOT` and loads unchanged on 26.3 (`api-version: '1.21.11'`). Input for M0-JAR-01.
- Cold boot to `ServerLoadEvent` takes about 35–40 s with all four plugins on 4 cores. BetterModel downloads the vanilla client jar on first start (needs network or a pre-seeded cache in CI).

## Decision
Use this harness for every M0 spike. M1-BUILD-03 reuses the same task layout for `:itest:runSelfTest`.
