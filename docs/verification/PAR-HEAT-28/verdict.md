# PAR-HEAT-28 verification

- Date: 2026-11-17
- Code under test: the PAR-HEAT-28 commit (`TorchService.LightRefresh`: one chunk light resend 5 region ticks after each
  burnout the commit queue applies).
- Unit evidence: `paper/src/test/java/dev/tfp/paper/light/TorchLightRefreshTest.java` (5 tests: a chunk-load burnout
  requests the resend of its chunk exactly `REFRESH_DELAY_TICKS` after the apply step and only once; two torches in one
  chunk share a resend, another chunk gets its own, a later burnout gets a new one; a torch still burning and a douse
  request none; the Paper refresh is safe on a server without the call). `./gradlew check` green.
- In-game check: run 2026-11-17 on commit `35ab107` (Paper 1.21.11, headless vanilla client, bot `Bot`); screenshots
  judged by the main session (Opus). Verdict: **pass** (below).

## In-game steps (Paper 1.21.11, headless client, bot `Bot`)

1. `weather clear 1000000; gamemode survival Bot; tfp debug thirst Bot 100; tfp debug hunger Bot 20`
2. `ce item give Bot tfp:torch 2`; place one on the floor and one on a wall; F3 (`debug-profile.json` with
   `minecraft:light_levels`) standing in each torch block: 14 block light.
3. `gamemode creative Bot; tfp time add days 4`; tp Bot 300 blocks away, wait 8 s, tp back (no client relog);
   `gamemode survival Bot`.
4. F3 standing in each dead torch block: 0 block light at once, without a relog; screenshots at GUI scale 2 and 3.

Criterion: a torch that burns out during chunk load reads 0 block light in F3 at once, with no relog.

Run: floor torch `tfp:torch` at 1056 70 2192, wall torch on a stone wall (F3 targeted block `minecraft:wall_torch` at
1056 71 2195). After the 4-day skip and the 300-block round trip both were `tfp:dead_torch` / `tfp:dead_wall_torch`
(the wall one shows as `big_dripleaf[tilt=full]` in F3). `./gradlew check` green on the same commit.

| criterion | result | evidence |
|---|---|---|
| Lit wall torch baseline: 14 block light | pass: `Client Light: 15 (15 sky, 14 block)` at Block 1056 70 2195, targeted `minecraft:wall_torch` | `01-gui2-lit-wall-f3-block14` |
| Lit floor torch baseline: 14 block light | not shown: standing at Block 1056 70 2192 read `11 block` (exactly the wall torch 3 blocks away), so this reading does not show the floor torch's own light. PAR-HEAT-21 read 14 at the same kind of spot. Not investigated in this run; see note | `02-gui2-lit-floor-f3-block11` |
| Dead wall torch: 0 block light at once, no relog | pass (GUI 2, same client session, no relog): `0 block` at the same spot that read 14 lit, targeted block now `big_dripleaf` | `03-gui2-dead-wall-f3-block0-no-relog` |
| Dead floor torch spot: 0 block light at once, no relog | pass (GUI 2, same session, no relog): `0 block` at 1056 70 2192, so no stale light from either torch remains there (the 11 from the wall torch is gone too) | `04-gui2-dead-floor-f3-block0-no-relog` |
| GUI scale 3 shots | pass with a caveat: both read `0 block`, but they were taken after a client relaunch, so they only confirm the server state, not the no-relog path | `05-gui3-...`, `06-gui3-...` |

Verdict: **pass.** The criterion asks for a torch that burns out on chunk load to read 0 block light without a relog: the
wall torch reads 14 lit and 0 dead at the same position in one client session with no relog (PAR-HEAT-21 read a stale 14
here before the relog). The dead floor spot also reads 0 with no relog. Caveats: the lit floor baseline read 11, not 14,
so the floor torch's own stale light is not shown either way; the GUI 3 readings came after a relaunch. PAR-HEAT-29
already saw 0 in one run before this fix, so the stale light may be intermittent. These runs cannot prove the resend is
the cause. The unit tests cover the resend request. Lossless WebP of every frame is in the session scratchpad
`ui/PAR-HEAT-28/`.

Note for the next torch run: the F3 light line needs `config/debug-profile.json` in the client game dir, format
`{"custom":{"minecraft:light_levels":"alwaysOn"}}` (camelCase values `alwaysOn`, `inOverlay`, `never`).

Repair round (2026-11-17): reviewer points addressed. The evidence above (committed in 450cc95) satisfies the screenshot
criterion; the item's Done line now cites it. PAR-HEAT-29's "re-run before changing TorchService" was not done before
35ab107, so whether the stale light still reproduced without the resend is unknown (recorded as a caveat). Code change in
the repair: `LightRefresh.PAPER` logs the first failed resend (one warning with the exception) instead of
swallowing it silently; the light behaviour verified above is unchanged.
