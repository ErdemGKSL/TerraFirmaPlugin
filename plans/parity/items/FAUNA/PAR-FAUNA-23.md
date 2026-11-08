---
id: PAR-FAUNA-23
title: "Fertilized egg incubation bar (TFC BarSystem EGG) on the shared item-bar layer"
area: FAUNA
status: open
wave: 44
deps: [PAR-HEAT-14, PAR-FAUNA-07]
size: S
aliases: ["parity audit 2026-11-08: client/BarSystem EGG bar named by no item"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/BarSystem.java"
  - "src/main/java/net/dries007/tfc/common/component/EggComponent.java"
  - "src/main/java/net/dries007/tfc/common/entities/livestock/OviparousAnimal.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/NestBoxBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/itembar/EggBar*.java"
  - "core/src/test/java/dev/tfp/core/itembar/EggBar*"
  - "paper/src/main/java/dev/tfp/paper/itembar/egg/**"
  - "paper/src/test/java/dev/tfp/paper/itembar/egg/**"
ui: true
in_game_steps:
  - "Give Bot a fertilized chicken egg (tfp debug egg Bot fertilized <hatchDay> if PAR-FAUNA-07 adds it, else from a mated hen); screenshot the hotbar slot at GUI scale 2 and 3 on the lay day and 4 days later (tfp time add days 4)"
  - "Give Bot an unfertilized egg; screenshot (no bar)"
plan:
  - "Current (audit 2026-11-08): fertilized eggs carry the TFC egg component (PAR-FAUNA-06/07) but show no bar. TFC draws a gold bar on a fertilized egg: width round(13 * incubationDays / 8), incubationDays = 8 - clamp(hatchDay - today, 0, 8); not shown on unfertilized eggs."
  - "Register the egg bar after the heat bar in PAR-HEAT-14's ItemBar registry (TFC order) with TFC's formula, reading the lazy egg state; the packet-time rendering, creative stripping and 'other bar wins' rule come from PAR-HEAT-14."
  - "Colour: vanilla colours by fraction, not gold: reuse PAR-HEAT-14's Divergences row (add the egg to it)."
tests:
  - "core: EggBarGoldenTest (widths for hatch days -1..10 around today, unfertilized invisible)"
  - "paper: a fertilized egg packet copy carries the bar, the server stack does not"
done_criteria:
  - "A fertilized egg shows a bar that grows over its 8 incubation days as TFC; an unfertilized egg shows none; tests green; screenshots pass"
---

# PAR-FAUNA-23: Fertilized egg incubation bar

> see_not_parity: NP-CLIENT-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
