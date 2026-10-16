# M0-JAR-01: One jar for 1.21.11 + 26.x
**Status:** done — 2026-10-16

## Setup
- Plugin: `spikes/jar-01` (`paper-plugin.yml` `api-version: '1.21.11'`, compiled against `paper-api 1.21.11-R0.1-SNAPSHOT`, Java 25 toolchain). Caffeine 3.2.2 is loaded at runtime through the paper-plugin library loader, not bundled.
- One artifact for all targets: `spikes/jar-01/build/libs/jar-01.jar`, built once with `./gradlew -q :jar-01:jar`.
- Targets: Paper 1.21.11-132-c5eb079, Paper 26.3-196-7ec11b2, Folia 1.21.11-14-529aabc, Folia 26.3-3-597e413. Java 25.0.4.1.
- Runs: `spikes/jar-01/run.sh` (`EXTRA_JVM=-Dtfp.spike.autostop=40`, port 25800) for the first pass. Folia 1.21.11 was rerun once with `EXTRA_JVM="-Dtfp.spike.autostop=40" timeout 600 ./client/server.sh folia 1.21.11 run/jar01/folia-1.21.11 25800 harness/build/libs/harness.jar jar-01/build/libs/jar-01.jar`.
- Folia 1.21.11 first attempt: 30 PASS / 24 FAIL, all 24 FAIL in the `enable` pass ("not reported within 20 s"). Archived as `spikes/run/jar01/folia-1.21.11/jar01-report.attempt1.txt` and `console.attempt1.out`. Its log was not grepped, because the rerun passed.

## Results
Rerun of Folia 1.21.11: 54 PASS / 0 FAIL. No timeouts, so no server-log grep was needed. The earlier "55 PASS" counts included the report's header line (`PASS|FAIL`); the real count is 54 per target (27 checks x 2 passes).

Reports: `spikes/run/jar01/<target>/jar01-report.txt`. Plugin-related exception lines: none in the four runs.

| check (enable + load+5s) | Paper 1.21.11 | Paper 26.3 | Folia 1.21.11 | Folia 26.3 |
|---|---|---|---|---|
| scheduler.async | PASS | PASS | PASS | PASS |
| scheduler.global | PASS | PASS | PASS | PASS |
| serverbuildinfo.ids | PASS | PASS | PASS | PASS |
| bukkit.getMinecraftVersion | PASS | PASS | PASS | PASS |
| version.family | PASS | PASS | PASS | PASS |
| adventure.component | PASS | PASS | PASS | PASS |
| adventure.minimessage | PASS | PASS | PASS | PASS |
| adventure.gson_plain_roundtrip | PASS | PASS | PASS | PASS |
| adventure.console_send | PASS | PASS | PASS | PASS |
| registry.biome | PASS | PASS | PASS | PASS |
| registry.block | PASS | PASS | PASS | PASS |
| registry.item | PASS | PASS | PASS | PASS |
| datacomponent.custom_model_data | PASS | PASS | PASS | PASS |
| datacomponent.lore | PASS | PASS | PASS | PASS |
| datacomponent.custom_name | PASS | PASS | PASS | PASS |
| itemstack.pdc | PASS | PASS | PASS | PASS |
| blockdata.from_string | PASS | PASS | PASS | PASS |
| material.cinnabar | PASS | PASS | PASS | PASS |
| material.oak_button | PASS | PASS | PASS | PASS |
| worldgen.classes | PASS | PASS | PASS | PASS |
| worldgen.limitedregion.probe | PASS | PASS | PASS | PASS |
| bootstrap.ran | PASS | PASS | PASS | PASS |
| loader.caffeine.classloader | PASS | PASS | PASS | PASS |
| loader.caffeine.cache | PASS | PASS | PASS | PASS |
| command.lifecycle | PASS | PASS | PASS | PASS |
| scheduler.region | PASS | PASS | PASS | PASS |
| chunk.pdc | PASS | PASS | PASS | PASS |

Static (`spikes/run/jar01/static-nms.txt`): no `net/minecraft` or `craftbukkit` reference in `jar-01.jar`. `static-refs.json` lists 93 Paper/Caffeine method references, resolved at runtime on every target.

### Version-family rule (from the reports)
`version.family` in the plugin: major `1` gives `major.minor` (`1.21`), otherwise `major` (`26`). Output: `family=1.21` on 1.21.11, `family=26` on 26.3, both Paper and Folia.

### API differences the reports show
- `material.cinnabar`: `matchMaterial("minecraft:cinnabar")` returns `null` on 1.21.11 (Paper and Folia) and `CINNABAR` on 26.3 (Paper and Folia). Only the 26.x side is present.
- `serverbuildinfo.ids`: `ServerBuildInfo` ids work on both lines (`1.21.11`, `26.3`); brand `Paper` or `Folia`.
- `worldgen.limitedregion.probe`: reflection finds `getBlockData(Vector)`, `setBlockData(Vector, BlockData)` and `getBlockState(Vector)` on all four targets, including 26.3. This contradicts the static note "26.3 lacks Vector block methods on LimitedRegion". The reports do not support that note, so it is not used here. Check the probe before relying on either claim.
- Adventure: the `adventure.*` checks pass on all four targets (component, MiniMessage, GSON round-trip, console send). The reports do not record Adventure jar versions, so "Adventure 4.x code on 5.x" is not proven as a version fact.
- `MAP_COLOR` removal and `SWING_ANIMATION` replacement in 26.3: no check in the reports covers them. UNVERIFIED.

## Decision / recommendation
**One jar works on 1.21.11 and 26.3.** The same `jar-01.jar` passes all four targets. Build against the lowest Paper API (1.21.11), keep `api-version: '1.21.11'`, and branch on the version family at runtime where an API differs (`material.cinnabar` is the only proven difference). No NMS is needed for the checks run here.

## Open / UNVERIFIED
- Adventure 5.x behaviour is not proven as a version fact (see above).
- `MAP_COLOR` / `SWING_ANIMATION` not checked.
- The `LimitedRegion` Vector-method claim conflicts with the probe. Re-check on 26.3 by hand before writing code that depends on either result.
- Folia 1.21.11 needed one rerun; the first attempt's timeouts were not diagnosed. The rerun did not reproduce them, so the cause (machine load) is not proven.
- Only one rerun was done. The result is a single 54/54 pass on the rerun target.
