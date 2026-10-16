# Spike agent guide (M0)

Read this before working on any spike in `spikes/`. Spike code is throwaway: it answers a question with measurements, nothing more. Production code starts in M1.

## Ground rules
- Work only inside your own spike folder (`spikes/<id>/`), plus your note `docs/spikes/<ID>.md`. Don't edit other spikes, `settings.gradle.kts`, `harness/`, `client/`, skills or design docs. If you need a shared change, say so in your report.
- **Never `git commit` or `git push`.** The main session reviews and commits.
- **Never `pkill`/`killall -f <pattern>`**: the pattern matches your own shell and kills it. Kill by PID (`client.pid`, `server.pid`) or stop servers with RCON `stop`.
- Don't guess APIs. Find them: `javap -cp <jar> <class>` (`/opt/jdk/25/bin/javap`), `unzip -l <jar> | grep ...`, the project skills in `.claude/skills/<name>/` (SKILL.md + references), and the plugin's own default config/resources in a run dir. If something can't be verified, write "UNVERIFIED" in the note instead of claiming it.
- Report numbers and observations exactly as measured. A failed check is a valid result. Don't make a check pass by changing what it tests.

## Environment
- Java 25: `export JAVA_HOME=/opt/jdk/25`. Build: `cd spikes && ./gradlew -q :<id>:jar` → `spikes/<id>/build/libs/<id>.jar`.
- Dependency jars (all `compileOnly`, already in your `build.gradle.kts`):
  - Paper API 1.21.11 (also runs on 26.3).
  - CraftEngine 26.9.2: `maven.modrinth:craftengine:mwFiNxVO`.
  - BetterModel 3.5.1: `maven.modrinth:bettermodel:RZkXlsnV`.
  - BetterHud 2.2.0-SNAPSHOT-449: `maven.modrinth:betterhud2:VHVKage9`.
  - PacketEvents 2.14.0: `maven.modrinth:packetevents:m78nFxYg`.
- The same jars are on disk in `/root/.gradle/caches/run-task-jars/plugins/paper/modrinth/<slug>/*.jar` (use them for `javap`).
- Server jars: `/root/.gradle/caches/run-task-jars/{paper,folia}/jars/<mc>/<build>.jar`.
- TFC source (pinned commit): `/opt/tfc` (assets under `src/main/resources/assets/tfc`).

## Running a server: `client/server.sh`
`./client/server.sh <paper|folia> <1.21.11|26.3> <run-dir> <port> [plugin jars...]` runs in the foreground (start it in the background with `( ... > log 2>&1 < /dev/null &)`).
- It copies CE/BM/BH/PacketEvents into `<run-dir>/plugins` and writes `server.properties`: offline mode, no whitelist, RCON on port+10 with password `spike`.
- `EXTRA_JVM="-Dkey=value ..."` passes JVM properties.
- Add `harness/build/libs/harness.jar` and set `EXTRA_JVM="-Dtfp.spike.autostop=<seconds>"` to stop the server automatically that many seconds after startup.
- Wait for startup: `timeout 300 bash -c 'until grep -q "Done (" <log>; do sleep 2; done'`.
- Console commands: `python3 client/rcon.py <port+10> <command...>`. Stop with `rcon.py <port+10> stop`.
- Pick ports from your assignment (prompt). Ports already in use by others: 25565, 25570, 25590, 25600, 25620, 25630.

**CraftEngine gotchas**
- CE extracts its default packs (`internal` mappings, `default_templates`) on its **first start only if `plugins/CraftEngine/resources/` doesn't exist yet**. Either start the server once without your files, then add them, or have your plugin write its pack folder in `onLoad()` (that runs after CE's own load, so CE has already extracted its defaults).
- CE never builds the resource pack at startup. After your pack files are in place and the server is up, run `rcon ce reload all` and wait ~20 s. That builds the pack and sends it to online players.
- For a local client set `resource-pack.self-host.ip: "127.0.0.1"` in `plugins/CraftEngine/config.yml` (the default `"auto"` may resolve to an address the client can't reach).
- CE config problems are logged as `Found N issue(s)` and `WARN ... <your id>` lines. Grep for them after every reload and report them.

## Real client: `client/session.sh`, `client/shot.sh`
- `./client/session.sh <mc> <port> <game-dir> <display>` starts Xvfb on `<display>` if needed, launches the official vanilla client (offline name `Bot`), joins the server, auto-accepts the server resource pack, and returns once `Bot` is online. The client PID is in `<game-dir>/client.pid`; its log is `<game-dir>/logs/latest.log`.
- `./client/shot.sh <out.png> <display>` takes a screenshot. **Look at every screenshot yourself with the Read tool** and describe what you actually see.
- Input: `DISPLAY=<display> xdotool key e` (keys), `xdotool mousemove X Y click 1` (mouse; the screen is 1280x720, GUI scale 2). Example: `xdotool key --delay 100 t` opens chat.
- The bot spawns wherever the world puts it. Build a test area at a known place (e.g. y=150 on a platform) and teleport there with `rcon tp Bot x y z yaw pitch`. Use `gamemode creative Bot` so it doesn't take damage. Use `time set noon` for day shots. `gamerule doDaylightCycle` doesn't exist on 26.x, so don't rely on it.
- The client renders with software Vulkan (26.3) or OpenGL (1.21.11), so it's slow: wait 10–15 s after teleporting before a screenshot.
- Use only your own display number (from your assignment), so two agents can run clients at the same time.

## Result note: `docs/spikes/<ID>.md`
```
# <ID>: <title>
**Status:** done | partial (what's missing) | failed — <date>
## Setup
what was built, which targets (Paper/Folia × 1.21.11/26.3), commands used
## Results
tables/numbers/observations, screenshots referenced by path (they stay in /tmp/claude-0, don't commit images)
## Decision / recommendation
what production code should do, based on the results
## Open / UNVERIFIED
```
Write the note yourself. Keep it factual and short (under ~120 lines).
