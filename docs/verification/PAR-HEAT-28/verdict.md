# PAR-HEAT-28 verification

- Date: 2026-11-17
- Code under test: the PAR-HEAT-28 commit (`TorchService.LightRefresh`: one chunk light resend 5 region ticks after each
  burnout the commit queue applies).
- Unit evidence: `paper/src/test/java/dev/tfp/paper/light/TorchLightRefreshTest.java` (5 tests: a chunk-load burnout
  requests the resend of its chunk exactly `REFRESH_DELAY_TICKS` after the apply step and only once; two torches in one
  chunk share a resend, another chunk gets its own, a later burnout gets a new one; a torch still burning and a douse
  request none; the Paper refresh is safe on a server without the call). `./gradlew check` green.
- In-game check: **pending, for the verify stage.** The implementing session had no subagent tool to delegate the server
  and client run to a Haiku agent (CLAUDE.md / PROTOCOL 5.3), so the run was left to the verify stage.

## Pending in-game steps (Paper 1.21.11, headless client, bot `Bot`)

1. `weather clear 1000000; gamemode survival Bot; tfp debug thirst Bot 100; tfp debug hunger Bot 20`
2. `ce item give Bot tfp:torch 2`; place one on the floor and one on a wall; F3 (`debug-profile.json` with
   `minecraft:light_levels`) standing in each torch block: 14 block light.
3. `gamemode creative Bot; tfp time add days 4`; tp Bot 300 blocks away, wait 8 s, tp back (no client relog);
   `gamemode survival Bot`.
4. F3 standing in each dead torch block: 0 block light at once, without a relog; screenshots at GUI scale 2 and 3.

Criterion: a torch that burns out during chunk load reads 0 block light in F3 at once, with no relog. Expect the client's
chunk to be resent about 5 ticks after the burnout (a brief chunk rebuild, no relog). Store the PNG crops (under 200 KB)
here and add the per-criterion pass/fail below.

| criterion | result | evidence |
|---|---|---|
| Dead floor torch: F3 0 block light at once, no relog (GUI 2 and 3) | pending | - |
| Dead wall torch: F3 0 block light at once, no relog (GUI 2 and 3) | pending | - |
