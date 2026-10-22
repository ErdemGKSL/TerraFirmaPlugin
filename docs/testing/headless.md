# Headless server + client recipe

The reliable way to see the plugin in a real vanilla client on this machine: a Paper server started by
`spikes/client/server.sh`, the official client under Xvfb started by `spikes/client/session.sh`, console commands over RCON.
Used by every `ui: true` parity item (PROTOCOL §6) and by manual checks. Runs are delegated to a cheap-model agent; the main
session judges the screenshots.

Verified 2026-10-22 (PAR-REL-01) on Paper 1.21.11 build 132 with the 1.21.11 client, CraftEngine 26.9.2, BetterModel 3.5.1,
BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0: the pack applied on the first join of a fresh client, 3 s after the join
(client log `Reloading ResourceManager: vanilla, server/...`, TFC HUD drawn, vanilla hearts and food hidden).

## 1. Build the jar

```
export JAVA_HOME=/opt/jdk/25
./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc   # content/generated is not in git
./gradlew :dist:shadowJar                                          # dist/build/libs/TerraFirmaPlugin-0.1.0-SNAPSHOT.jar
```

Without the datagen step the jar has no `/content` and the plugin fails with `content/data/id_map.json is missing`.

## 2. Pick free ports and a display

Other agents run servers here. Choose a server port `P` (RCON is `P+10`), a CraftEngine pack port (`PACK_PORT`, default
8163; give each parallel server its own) and an X display no one uses (`ls /tmp/.X11-unix/`). `server.sh` refuses to start
when one of the three ports is taken.

## 3. Start, prime, restart

```
R=<run-dir>   # a fresh folder in the scratchpad
TFP_WORLD=1 spikes/client/server.sh paper 1.21.11 $R $P dist/build/libs/TerraFirmaPlugin-0.1.0-SNAPSHOT.jar > $R.log 2>&1 &
# wait for "Done (" in $R.log, then for "Generated resource pack" (about 30 s later)
spikes/client/stop.sh $R $P
rm -rf $R/world $R/world_nether $R/world_the_end
spikes/client/server.sh paper 1.21.11 $R $P > $R.log2 2>&1 &
# wait for "Done ("
```

What `server.sh` does before the first start:
- `server.properties`: offline mode, no whitelist, RCON on `P+10` with password `spike`.
- `TFP_WORLD=1`: writes `bukkit.yml` with `worlds: world: generator: TerraFirmaPlugin` (without it the default world is
  vanilla terrain and season, climate and worldgen checks see nothing).
- CraftEngine `config.yml`: seeded from the CraftEngine jar's default if CraftEngine has not written one yet, then
  `resource-pack.self-host.ip: "127.0.0.1"`, `port: $PACK_PORT`, `one_time_token: false`. Every start re-applies these.

Why the priming start: the first start installs our content and changes `misc.delay-configuration-load` in CraftEngine's
config (the log shows `RESTART REQUIRED`); until the restart the plugin runs degraded and TFC chunks are not generated, so
the world of the first start is deleted. The first start also builds the CraftEngine pack (`Rebuilding the CraftEngine
resource pack (first install ...)`), which the second start serves from `plugins/CraftEngine/cache/hosted`.

## 4. Join

```
spikes/client/session.sh 1.21.11 $P <game-dir> :<display>
```

It prints `joined after Nx3s`, then waits up to `PACK_WAIT` seconds (default 120, 0 skips) for the pack and prints
`pack applied after Nx3s`. Exit 0: in the world with the pack; 1: no join; 2: the pack failed or did not apply. The
`servers.dat` entry has `acceptTextures=1`, so there is no prompt; if one shows anyway, the script presses Tab and Return
(the first button, Proceed) at 15, 30 and 60 s.

Afterwards: `spikes/client/shot.sh <png> :<display>` for screenshots, `python3 spikes/client/rcon.py $((P+10)) <command>`
for console commands, `DISPLAY=:<display> xdotool key ...` for keys (F1 hides the HUD).

## 5. Snow-free test site (colour measurements)

```
spikes/client/testroom.sh $((P+10)) <x> <floor-y> <z>
```

Builds a 13x13 grass floor at `floor-y` with everything above it cleared to `y+12`, a 15x15 barrier roof at `y+8`, a strip
of persistent oak leaves (foliage tint) and a 3x3 water pool (water tint), sets clear weather and noon, and puts Bot in
creative at the south edge looking north at the floor. Snow cannot reach the floor: the barrier roof is the column's
`MOTION_BLOCKING` top, so `SnowService` samples the roof, and a snow layer never rests on barrier (vanilla's
`snow_layer_cannot_survive_on`, kept in `SnowService.SNOW_CANNOT_SURVIVE_ON`). Barrier is invisible and passes sky light,
so the floor keeps full daylight.

Choose a low column (y about 64 to 80): the season tint follows the column's climate and high columns are cold enough to
stay under snow all autumn (`plans/gaps.md`, M2-SEA-02 notes). Checked 2026-10-22 at spawn (2608, 65, -3200): June 5.2 C,
then `tfp time add months 7` to January at -17.8 C; the floor stayed snow-free and the grass changed from summer green to the
winter tint. The water may freeze in a cold month. Paper only: Folia's console cannot `fill` across regions.

## 6. Stop

`spikes/client/stop.sh $R $P` (waits for `Done (`, sends RCON `stop`, waits for the process to exit), then kill the client by
`<game-dir>/client.pid`. Never `pkill -f`. Never stop a server with a signal while it is still starting.

### CraftEngine "Asynchronous Chunk getEntities call" at shutdown

Not our code. Reproduced 2026-10-22 with no TerraFirmaPlugin jar (CraftEngine, BetterModel, BetterHud and PacketEvents
only): SIGTERM after `Done preparing level` but before `Done (` makes Paper run the shutdown on `Thread-0`
(`ServerShutdownThread`), and CraftEngine's `BukkitFurnitureManager.disable` (line 170, from `ServerPreShutdownEvent` and
again from `onDisable`) calls `World.getEntities`, which the async catcher rejects. The same SIGTERM after `Done (` and RCON
`stop` at any time shut down on the server thread with no error (CraftEngine-only run and a TerraFirmaPlugin run,
2026-10-22). The 2026-10-21 `steelsw` case was a stop during startup. Harness fix: stop through `stop.sh`;
`spikes/run-matrix.sh` counts these lines as `ce_early_stop` instead of errors. Upstream report (to be filed with
CraftEngine by the project owner): "26.9.2 on Paper 1.21.11: stopping the server before startup completes calls
`BukkitFurnitureManager.disable` on the shutdown thread; `World#getEntities` throws `Asynchronous Chunk getEntities call`.
Skip the entity scan when not on the main thread, or schedule it."

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Missing-glyph boxes across the top, vanilla hearts visible | Pack not applied: self-host `ip: "auto"` resolved to an address the client cannot reach | `server.sh` sets 127.0.0.1; check `plugins/CraftEngine/config.yml` and `Netty HTTP server started on port` in the log |
| Client log `pack(s) failed to download`, HTTP 403 | One-time download token spent by an earlier attempt or rejoin | `server.sh` sets `one_time_token: false` |
| `FAILED TO BIND TO PORT` crash report | Port used by another agent's server | pick another `P`; `server.sh` now checks first |
| `content/data/id_map.json is missing` | Jar built without datagen output | step 1 |
| Season or worldgen checks show vanilla terrain | No `generator: TerraFirmaPlugin` in `bukkit.yml` | `TFP_WORLD=1` on a fresh run dir |
| `xdpyinfo` not found | Not installed here | harmless: `session.sh` then always tries to start Xvfb, which fails quietly when the display exists |
