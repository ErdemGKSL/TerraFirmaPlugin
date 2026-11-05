---
id: PAR-INFRA-32
title: "Renderer-cube mining time: TFC break time independent of the client's swing rate, measured on a full-speed client"
area: INFRA
status: open
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
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11 and Paper 26.3 + client 26.3 with the S2 spike pack (spikes/s2-destroy-stages, docs/spikes/S2-destroy-stages.md §2); survival; Bot with tfp:metal/pickaxe/bronze"
  - "Break tfps2:metal/block/copper five times with the pickaxe, twice by hand and twice with a wrong tool (e.g. a bronze axe; formula 500 ticks like the hand), and a vanilla iron block (same hardness) five times as the control; record the `[S2] break ... ticks=` lines and the client's tick rate (F3)"
plan:
  - "Current (PAR-INFRA-17): CraftEngine computes the progress of a renderer cube (barrier carrier, the client cannot break it) with TFC's formula, but advances it only on server ticks in which the client swung its arm (`BukkitServerPlayer.tick` -> `hasSwingHand()` -> `tickBlockDestroy`). On the headless clients (llvmpipe, ~15 fps) a copper block took 23-27 ticks with a bronze pickaxe (formula 21, vanilla iron block 18-19) on 1.21.11 and 35-38 on 26.3, 582 ticks by hand (formula 500)."
  - "First measure on a client that holds 20 ticks/s (lighter scene, or a faster GPU path); if the gap is within one tick-rounding plus the vanilla latency, close this item with the measurement and keep the ledger divergence as 'depends on the client tick rate'."
  - "Otherwise add a server-time progress for renderer cubes (decide async, apply sync): track dig start/abort per player and block, compute TFC's progress from server ticks elapsed (the vanilla server's own rule), and finish the break through CraftEngine when it reaches 1; keep CE's destroy_stages display in step. Wire through a FeatureModule."
tests:
  - "paper: unit test of the elapsed-tick progress against TFC's formula (hand, correct tool, wrong tool, hardness 5)"
done_criteria:
  - "Copper block break time with hand, correct tool and wrong tool matches TFC's formula within one tick-rounding plus the vanilla iron-block control's latency on 1.21.11 and 26.3; evidence in docs/verification/PAR-INFRA-32/"
---

# PAR-INFRA-32: Renderer-cube mining time: TFC break time independent of the client's swing rate, measured on a full-speed client

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
