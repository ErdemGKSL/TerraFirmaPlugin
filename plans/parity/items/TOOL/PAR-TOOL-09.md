---
id: PAR-TOOL-09
title: "Chisel: smoothing, slabs and stairs with mode cycling"
area: TOOL
status: open
wave: 47
deps: [PAR-TOOL-06, PAR-DECO-06]
size: M
aliases: ["M4-CHIS-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/ChiselItem.java"
  - "src/main/java/net/dries007/tfc/common/player/ChiselMode.java"
  - "src/main/java/net/dries007/tfc/network/CycleChiselModePacket.java"
  - "src/generated/resources/data/tfc/recipe/chisel"
files_owned:
  - "core/src/main/java/dev/tfp/core/chisel/**"
  - "core/src/test/java/dev/tfp/core/chisel/**"
  - "core/src/test/resources/dev/tfp/core/chisel/**"
  - "paper/src/main/java/dev/tfp/paper/chisel/**"
  - "paper/src/test/java/dev/tfp/paper/chisel/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/chisel/copper; hammer in offhand"
  - "sneak+F to cycle; right-click raw granite; screenshot"
plan:
  - "Mode cycle via sneak + swap-hand (PlayerSwapHandItemsEvent cancelled) shown on the actionbar; right-click with hammer in offhand applies ChiselRules; collapse trigger check (enableChiselsStartCollapses) through PAR-PHYS-01."
tests:
  - "paper: MockBukkit mode cycle and conversion"
done_criteria:
  - "Chiselling raw granite makes smooth granite, then a slab"
---

# PAR-TOOL-09: Chisel: smoothing, slabs and stairs with mode cycling

> see_not_parity: NP-UI-01, NP-CLIENT-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
