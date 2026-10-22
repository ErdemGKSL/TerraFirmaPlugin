---
id: PAR-PROC-04
title: "Quern: block, handstone, grinding, GUI-less interaction"
area: PROC
status: open
wave: 7
deps: [PAR-INFRA-02, PAR-INFRA-06, PAR-INFRA-13]
size: M
aliases: ["M3-PROC-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/QuernBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java"
  - "src/generated/resources/data/tfc/recipe/quern"
files_owned:
  - "core/src/main/java/dev/tfp/core/quern/**"
  - "core/src/test/java/dev/tfp/core/quern/**"
  - "core/src/test/resources/dev/tfp/core/quern/**"
  - "paper/src/main/java/dev/tfp/paper/quern/**"
  - "paper/src/test/java/dev/tfp/paper/quern/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/quern/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/quern/**"
  - "content/generated/content/craftengine/tfp/configuration/quern/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:quern"
  - "add handstone; add grain; right-click handstone; screenshot"
plan:
  - "CE quern with handstone item display (entity_renderer) spinning only while used (interpolated rotation steps on interaction); right-click with input places it, right-click handstone grinds (QuernRules 90 ticks), handstone damage per grind, output pops out; RotationSink hook left for PAR-MECH-06."
tests:
  - "paper: MockBukkit grind"
  - "core: QuernGoldenTest exists"
done_criteria:
  - "Grinding wheat grain gives flour in-game"
---

# PAR-PROC-04: Quern: block, handstone, grinding, GUI-less interaction

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
