---
id: PAR-INFRA-32
title: "Renderer-cube mining time: TFC break time independent of the client's swing rate, measured on a full-speed client"
area: INFRA
status: done
wave: 6
deps: [PAR-INFRA-17]
size: S
aliases: ["follow-up of PAR-INFRA-17 (spike S2 mining time partial)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/Metal.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/rendered/**"
  - "paper/src/test/java/dev/tfp/paper/rendered/**"
  - "docs/verification/PAR-INFRA-32/**"
  - "integration/craftengine/src/main/java/dev/tfp/integration/craftengine/CraftEngineIntegration.java"
  - "integration/craftengine/src/main/java/dev/tfp/integration/craftengine/CraftEngineBehaviours.java"
  - "integration/craftengine/src/test/java/dev/tfp/integration/craftengine/**"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11 and Paper 26.3 + client 26.3 with the S2 spike pack (spikes/s2-destroy-stages, docs/spikes/S2-destroy-stages.md §2); survival; Bot with tfp:metal/pickaxe/bronze"
  - "Break tfps2:metal/block/copper five times with the pickaxe, twice by hand and twice with a wrong tool (e.g. a bronze axe; formula 500 ticks like the hand), and a vanilla iron block (same hardness) five times as the control; record the `[S2] break ... ticks=` lines and the client's tick rate (F3)"
plan:
  - "UNBLOCKED (manager, 2026-11-06): files_owned now includes the CraftEngine integration adapter files (CraftEngineIntegration, CraftEngineBehaviours, tests) so the server-time progress hook can be added there. Touch only what the hook needs; feature code reaches CraftEngine only through these adapters."
  - "BLOCKED (2026-11-05): step 1 measured and failed, step 2 needs files outside files_owned. Measurement (Paper 1.21.11, client 1.21.11 at 34 fps / server 20.0 TPS, light scene: render distance 2, fast graphics, no clouds, S2 pack, flat stone floor, peaceful): copper renderer cube with tfp:metal/pickaxe/bronze 23, 22, 24, 21, 22 ticks (formula 21; vanilla iron block control 19, 20, 18, 20, 18, 20, 18), by hand 562, 529 (formula 500; vanilla iron block by hand 463, 467), bronze axe 541, 561 (formula 500): the correct tool passes, hand and wrong tool miss by 29-62 ticks, beyond one tick plus the control's 35-tick offset, so even a full-speed client loses CE progress ticks. Cause (CraftEngine 26.9.2 bytecode): BukkitServerPlayer.tick advances miningProgress only when hasSwingHand() (swingHandAck && lastSwingHandTick + 2 > gameTicks) and then clears swingHandAck, so two swing packets landing in one server tick count once and a tick with none counts zero. The fix (server-time progress) must drive CraftEngine's own progress so its destroy_stages stay in step: once per server tick, while CE's BukkitServerPlayer.isMiningBlock() is true and the client swung within a grace window (PlayerAnimationEvent), call CE's Player.onSwingHand() (BukkitAdaptor.adapt(player)), never in adventure/creative/spectator and never while not mining (CE's 'awfulBreakFixer' would restart mining from a ray trace). That is a CraftEngine call, which feature code may make only through an integration adapter: it needs a new method on an interface in paper/src/main/java/dev/tfp/paper/integration/ (e.g. CustomBlocks or a new MiningDriver) and its implementation in integration/craftengine/src/main/java/dev/tfp/integration/craftengine/ (plus the FakeCustomBlocks test fake), none of which are in files_owned. Unblock: add those paths (or a new MiningDriver interface file and a CE implementation file) to files_owned; then the rendered/ module (tracker + elapsed-tick model + FeatureModule) and the 1.21.11/26.3 re-measurement follow the plan below."
  - "Current (PAR-INFRA-17): CraftEngine computes the progress of a renderer cube (barrier carrier, the client cannot break it) with TFC's formula, but advances it only on server ticks in which the client swung its arm (`BukkitServerPlayer.tick` -> `hasSwingHand()` -> `tickBlockDestroy`). On the headless clients (llvmpipe, ~15 fps) a copper block took 23-27 ticks with a bronze pickaxe (formula 21, vanilla iron block 18-19) on 1.21.11 and 35-38 on 26.3, 582 ticks by hand (formula 500)."
  - "First measure on a client that holds 20 ticks/s (lighter scene, or a faster GPU path); if the gap is within one tick-rounding plus the vanilla latency, close this item with the measurement and keep the ledger divergence as 'depends on the client tick rate'."
  - "Otherwise add a server-time progress for renderer cubes (decide async, apply sync): track dig start/abort per player and block, compute TFC's progress from server ticks elapsed (the vanilla server's own rule), and finish the break through CraftEngine when it reaches 1; keep CE's destroy_stages display in step. Wire through a FeatureModule."
  - "Done: server-time mining for CraftEngine-dug blocks: paper/.../rendered/ (MiningDriver + MiningDrivers holder, MiningTime formula, MiningSession per-tick decision, ServerTimeMining listener with a per-player entity task, RenderedMiningModule) and CraftEngineIntegration as the MiningDriver (isMiningBlock && !clientSideCanBreak; onSwingHand once per server tick while the client digs). The driver is published through MiningDrivers because Integrations.java (no generic service lookup) is outside files_owned. Tests: MiningTimeTest 3, MiningSessionTest 5, ServerTimeMiningSimulationTest 3, ServerTimeMiningMockBukkitTest 6. In game 1.21.11 (60 and 10 fps) and 26.3 (58 fps): copper 21-22 ticks with the bronze pickaxe (formula 21), 501-503 by hand and with the bronze axe (formula 501 summed); docs/verification/PAR-INFRA-32/verdict.md. Ledger divergence row rewritten (revisit: no)."
tests:
  - "paper: unit test of the elapsed-tick progress against TFC's formula (hand, correct tool, wrong tool, hardness 5)"
done_criteria:
  - "Copper block break time with hand, correct tool and wrong tool matches TFC's formula within one tick-rounding plus the vanilla iron-block control's latency on 1.21.11 and 26.3; evidence in docs/verification/PAR-INFRA-32/"
---

# PAR-INFRA-32: Renderer-cube mining time: TFC break time independent of the client's swing rate, measured on a full-speed client

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
