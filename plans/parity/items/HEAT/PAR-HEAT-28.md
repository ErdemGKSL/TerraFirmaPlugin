---
id: PAR-HEAT-28
title: "Torch burnout on chunk load leaves the client's block light at 14 until the chunk is sent again"
area: HEAT
status: open
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
  - "weather clear 1000000; gamemode survival Bot; tfp debug thirst Bot 100; tfp debug hunger Bot 20"
  - "ce item give Bot tfp:torch 2; place one on the floor and one on a wall; F3 (debug-profile.json with minecraft:light_levels) standing in each torch block: 14 block light"
  - "gamemode creative Bot; tfp time add days 4; tp Bot 300 blocks away, wait 8 s, tp back (no client relog); gamemode survival Bot"
  - "F3 standing in each dead torch block: 0 block light at once, without a relog; screenshots at GUI scale 2 and 3"
plan:
  - "Found by PAR-HEAT-21's in-game run (2026-11-17, Paper 1.21.11, docs/verification/PAR-HEAT-21/18-gui2-dead-floor-f3-stale-before-relog.png): after the 4-day skip and the 300-block round trip, both torches showed the dead look but F3 still read 14 block light standing in the dead floor and dead wall torch blocks. After a client relog the same blocks read 0, so the server's light is right and the client keeps the light of the chunk it was sent before the swap."
  - "Find where the light goes stale: the chunk-load catch-up applies the dead torch (region commit queue) after the chunk packet with the old light was sent, and no light update reaches the client (or the client relights nothing because it never saw the lit state). Check whether TorchService's commit sets the CE block with light updates, whether Paper/Moonrise sends the section light change to players already tracking the chunk, and whether a forced light resend (ClientboundLightUpdatePacket for the section, or a chunk resend for the tracking players) after the swap fixes it. Keep it to the torches that changed (no per-chunk resend without a change)."
tests:
  - "paper: MockBukkit or an itest case that the burnout commit requests the light refresh for the changed position"
done_criteria:
  - "A torch that burns out during chunk load reads 0 block light in F3 at once, with no relog (screenshots, docs/verification/PAR-HEAT-28/)"
---

# PAR-HEAT-28: Stale client light after a torch burns out on chunk load

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
