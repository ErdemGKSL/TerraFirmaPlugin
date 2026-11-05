# PAR-HUD-07 verification

- Date: 2026-11-05
- Tree: the PAR-HUD-07 commit on `push-to-main/dreamy-heisenberg-f5azym` (parent 731f8aa)
- Server: Paper 26.3 (run-paper cache build 196), CraftEngine, BetterModel, BetterHud and PacketEvents from the run-paper
  plugin cache, TFC world (`TFP_WORLD=1`), visual profile level-2
- Client: official 26.3 client (`/opt/mcclient/versions/26.3`), headless under Xvfb, 1280x720, bot `Bot`

## Steps

1. `spikes/client/server.sh paper 26.3 ...` priming start, stop, world deleted, second start (docs/testing/headless.md).
2. `spikes/client/session.sh 26.3 ...` at GUI scale 2: joined; the client log shows
   `Reloading ResourceManager: vanilla, vanilla, server/...` (pack applied). `session.sh` waits for the 1.21.11 wording
   `vanilla, server` and so reported "pack not applied" on 26.3: a harness wording issue, the pack was on (the TFC HUD and
   the plate are drawn).
3. RCON `gamemode survival Bot`, `time set noon`, `weather clear`; screenshot -> `gui2-26.3-hotbar.png`.
4. Relaunch with `MC_OPTIONS=guiScale:3`; RCON `tfp debug hunger Bot 9`, `damage Bot 5`; screenshot ->
   `gui3-26.3-hotbar-damaged-hungry.png`, 4x crop of the health bar -> `gui3-26.3-health-4x.png`.

## Criteria

| criterion | result |
|---|---|
| 26.x screenshot shows no vanilla hearts/food | **pass**: at GUI scale 2 (full health and food) and 3 (570/761 health, hunger 9) no heart, half heart, container, food or armour icon is drawn; only the TFC bars (health with text, thirst, hunger) above the vanilla XP bar and hotbar |
| tools/datagen: per-version sprite list exists | **pass**: `HiddenVanillaSpritesTest` (5 tests, incl. both family lists equal to their client jars) |

Judged by the main session from the PNGs in this folder.
