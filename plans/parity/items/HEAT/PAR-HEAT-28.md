---
id: PAR-HEAT-28
title: "Torch burnout on chunk load leaves the client's block light at 14 until the chunk is sent again"
area: HEAT
status: done
wave: 16
deps: [PAR-HEAT-21]
size: S
aliases: ["follow-up of PAR-HEAT-21 (in-game check 2026-11-17)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCTorchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/DeadTorchBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/light/TorchService.java"
  - "paper/src/main/java/dev/tfp/paper/light/TorchDevice.java"
  - "paper/src/test/java/dev/tfp/paper/light/TorchLightRefreshTest.java"
ui: true
in_game_steps:
  - "Before each client launch write <game-dir>/debug-profile.json (the client game dir itself, NOT config/; 1.21.11 reads new File(gameDir, \"debug-profile.json\") at start) with {\"custom\":{\"minecraft:light_levels\":\"alwaysOn\",\"minecraft:looking_at_block\":\"alwaysOn\",\"minecraft:player_position\":\"alwaysOn\"}}; F3 then shows a 'Client Light: N (S sky, B block)' line. Launch with MC_OPTIONS=guiScale:2. Build the test spot on land: a stone floor and a 3-high stone wall (fill ... stone) at a dry low column (check with tfp debug chunk / F3 that it is not ocean), tp Bot only after a 5 s wait for chunk load"
  - "weather clear 1000000; gamemode survival Bot; tfp debug thirst Bot 100; tfp debug hunger Bot 20"
  - "ce item give Bot tfp:torch 2; place one on the floor and one on the wall at least 6 blocks apart (so one torch's light does not mask the other's reading); F3 standing in each torch block: 14 block light"
  - "gamemode creative Bot; tfp time add days 4; tp Bot 300 blocks away (same land, y from the heightmap), wait 8 s, tp back (no client relog); gamemode survival Bot"
  - "F3 standing in each dead torch block: 0 block light at once, without a relog; screenshots at GUI scale 2. For GUI scale 3 relaunch with MC_OPTIONS=guiScale:3 (debug-profile.json still in place), place two fresh torches 16+ blocks from the first pair and repeat steps 3-5 in that one session, so the GUI 3 readings are also no-relog"
plan:
  - "Found by PAR-HEAT-21's in-game run (2026-11-17, Paper 1.21.11, docs/verification/PAR-HEAT-21/18-gui2-dead-floor-f3-stale-before-relog.png): after the 4-day skip and the 300-block round trip, both torches showed the dead look but F3 still read 14 block light standing in the dead floor and dead wall torch blocks. After a client relog the same blocks read 0, so the server's light is right and the client keeps the light of the chunk it was sent before the swap."
  - "Find where the light goes stale: the chunk-load catch-up applies the dead torch (region commit queue) after the chunk packet with the old light was sent, and no light update reaches the client (or the client relights nothing because it never saw the lit state). Check whether TorchService's commit sets the CE block with light updates, whether Paper/Moonrise sends the section light change to players already tracking the chunk, and whether a forced light resend (ClientboundLightUpdatePacket for the section, or a chunk resend for the tracking players) after the swap fixes it. Keep it to the torches that changed (no per-chunk resend without a change)."
  - "Note from PAR-HEAT-29 (2026-11-17, Paper 1.21.11, docs/verification/PAR-HEAT-29/11-gui3-chunk-load-burnout-f3-block0.png): with the new dead torch carriers (torchflower_crop[age=1], big_dripleaf[tilt=full]) the same steps read 0 block light in F3 standing in both dead torch blocks at once, without a relog (one run). The stale light may be fixed or intermittent: re-run the in-game steps before changing TorchService."
  - "Done: TorchService queues one chunk light resend (LightRefresh; Paper World.refreshChunk to the players seeing the chunk, none when nobody does) REFRESH_DELAY_TICKS (5) region ticks after each burnout the commit queue applies, deduplicated per chunk while pending; nothing for a torch still burning, a douse or a relight. TorchModule's constructor call is unchanged (the old constructor defaults to LightRefresh.PAPER). Tests: paper light/TorchLightRefreshTest (5). ./gradlew check green. In game (2026-11-17, Paper 1.21.11, on 35ab107): pass, docs/verification/PAR-HEAT-28/ (01-06 + verdict.md): wall torch 14 lit, 0 dead at the same spot in one client session with no relog (GUI 2); dead floor spot 0 with no relog. PAR-HEAT-29's note asked for a re-run before changing TorchService; that run was not made first, so the run after the change cannot show the stale light was still reproducible (PAR-HEAT-29 already read 0 once) — the resend stays as a cheap guard (one chunk resend per burnout chunk, only with viewers). Open caveats: lit floor baseline read 11 (the wall torch's light), so the floor torch's own stale-light case is unshown; GUI 3 shots came after a relaunch. Repair: LightRefresh.PAPER logs one warning (with the exception) the first time the resend fails instead of swallowing it silently. Repair 2 (re-run on b84781e found no F3 light line): the debug profile belongs in <game-dir>/debug-profile.json, not config/ (checked in the 1.21.11 client jar); in_game_steps and verdict.md corrected, steps now also fix the test spot, torch spacing and a no-relog GUI 3 pass. No code change."
  - "UNBLOCKED (2026-11-17, Opus unblocker): not a files_owned or dependency limit. Both rejections were about the in-game evidence, not the code: the 35ab107 run left the floor baseline unshown (read the wall torch's 11) and its GUI 3 shots came after a relaunch; the b84781e re-run put debug-profile.json under config/ so F3 had no light line, on an ocean spawn. The code (TorchService LightRefresh, TorchLightRefreshTest 5 tests) stays as is. Next worker: no code change unless the run fails; on current HEAD run the corrected in_game_steps exactly (debug-profile.json in the client game dir, land spot with stone floor and wall, torches 6+ blocks apart, GUI 3 pass with fresh torches in the relaunched session), and replace verdict.md with that run: lit 14 for both torches, dead 0 for both with no relog at GUI 2 and GUI 3. If the stale 14 reproduces, fix inside files_owned. Then set status done again."
  - "Done (re-close 2026-11-17 after the unblock): no code change; TorchService LightRefresh and TorchLightRefreshTest (5) unchanged, ./gradlew check green. In game (2026-11-17, Paper 1.21.11, on 36bcc70, corrected in_game_steps: game-dir debug-profile.json, land spot, torches 9 apart): pass, docs/verification/PAR-HEAT-28/ (01-08 + verdict.md): floor and wall torch read 14 block light lit and 0 dead after the 4-day skip and 300-block round trip with no relog, at GUI 2 and in a GUI 3 pass with fresh torches placed and burnt out in the relaunched session. The old 35ab107 evidence is replaced."
tests:
  - "paper: MockBukkit or an itest case that the burnout commit requests the light refresh for the changed position"
done_criteria:
  - "A torch that burns out during chunk load reads 0 block light in F3 at once, with no relog (screenshots, docs/verification/PAR-HEAT-28/)"
---

# PAR-HEAT-28: Stale client light after a torch burns out on chunk load

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
