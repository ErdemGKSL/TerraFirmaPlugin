---
id: PAR-FLUID-14
title: "/tfp debug water names hot spring pool water spring"
area: FLUID
status: open
wave: 12
deps: [PAR-FLUID-04]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/HotWaterBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldwater/WorldWaterModule.java"
  - "paper/src/test/java/dev/tfp/paper/worldwater/DebugWaterSpringMockBukkitTest.java"
ui: false
in_game_steps: []
plan:
  - "Found by PAR-FLUID-11 (in-game, `docs/verification/PAR-FLUID-11/verdict.md`): standing in a hot spring pool that heals (old_mountains, 1421 98 -1536, seed 12345), `/tfp debug water Bot` answers `fresh water`. `WorldWaterModule.describe` reads `ChunkWater.kind(x, y, z)`, which classifies with `springPool = false`; the pool rule lives in PAR-FLUID-04's `SpringWater` service (`paper/src/main/java/dev/tfp/paper/worldwater/hot/SpringWater.java`, looked up through the module services). Fix: when the `SpringWater` service is present and says the block at the player's feet is spring water, report `spring`; otherwise keep the current answer. Read on the player's thread as now. No `BootstrapTest` change: the `water` debug literal already exists."
tests:
  - "paper: MockBukkit: a player in a gravel-bedded pool above sea level in an active spring chunk gets `spring water`; on stone-bedded water the answer is unchanged"
done_criteria:
  - "`/tfp debug water <name>` reports spring water inside a hot spring pool (MockBukkit test)"
---

# PAR-FLUID-14: /tfp debug water names hot spring pool water spring

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
