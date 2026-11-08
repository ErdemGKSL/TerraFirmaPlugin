---
id: PAR-FLUID-11
title: "Hot spring in-game verification: health rises standing in a hot spring"
area: FLUID
status: open
wave: 11
deps: [PAR-FLUID-04]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/HotWaterBlock.java"
files_owned:
  - "docs/verification/PAR-FLUID-11/**"
ui: true
in_game_steps:
  - "locate a hot spring: an old mountains, canyons or volcanic mountains area of a TFC world with a shallow pool above y 63 on gravel (`tfp debug water Bot` reports the biome); tp Bot into the middle of the pool"
  - "damage Bot 10 (e.g. `damage Bot 10 minecraft:generic`); screenshot the health bar (GUI scale 2)"
  - "stand in the pool 10 s; screenshot the health bar again"
plan:
  - "Deferred in-game check of PAR-FLUID-04 (PROTOCOL §6.5): the worker session that built PAR-FLUID-04 had no subagent tool to run the headless server and client, so the screenshot check moved here. Run it on the current branch; a failure is fixed in PAR-FLUID-04's files (`paper/.../worldwater/hot/**`) through a follow-up."
tests:
  - "none (in-game check only)"
done_criteria:
  - "Health after 10 s in the pool is higher than right after the damage (about +1.6 per touched spring block over 10 s, natural regeneration aside: compare with 10 s on dry land)"
---

# PAR-FLUID-11: Hot spring in-game verification: health rises standing in a hot spring

> see_not_parity: NP-FLUID-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
