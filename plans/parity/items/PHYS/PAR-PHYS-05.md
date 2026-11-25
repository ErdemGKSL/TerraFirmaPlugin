---
id: PAR-PHYS-05
title: "Explosion collapses"
area: PHYS
status: done
wave: 18
deps: [PAR-PHYS-02]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/util/tracker/Collapse.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/collapse/explosion/**"
  - "paper/src/test/java/dev/tfp/paper/collapse/explosion/**"
ui: true
in_game_steps:
  - "summon minecraft:tnt in a raw rock cave; screenshot"
plan:
  - "EntityExplodeEvent/BlockExplodeEvent start collapses at the blast as TFC onExplosionDetonate."
  - "Done: ExplosionCollapseListener (NORMAL, below PowderkegBlasts HIGH) + CollapseService.explosion (one public method added outside files_owned, no other item in flight on it); 7 paper tests; ledger row + 2 divergences."
  - "Pending in-game check (container cannot run client)"
tests:
  - "paper: MockBukkit explosion enqueues"
done_criteria:
  - "TNT underground triggers a collapse"
---

# PAR-PHYS-05: Explosion collapses

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
