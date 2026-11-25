---
id: PAR-DECO-29
title: "Crate device, buoys and cake blocks wired in Paper (follow-up of PAR-DECO-24)"
area: DECO
status: open
wave: 50
deps: [PAR-DECO-24]
size: M
aliases: ["split from PAR-DECO-24: CrateRules and CakeRules shipped there"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/CrateBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/BuoyBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCCakeBlock.java"
  - "src/main/java/net/dries007/tfc/client/CrateOverlays.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/misc/**"
  - "core/src/test/java/dev/tfp/core/misc/**"
  - "paper/src/main/java/dev/tfp/paper/misc/**"
  - "paper/src/test/java/dev/tfp/paper/misc/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/misc/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/misc/**"
  - "content/generated/content/craftengine/tfp/configuration/misc/devices*"
ui: true
in_game_steps:
  - "tfp debug showcase misc"
plan:
  - "Crate: inventory (TFC slots, crateEnableAutomation hopper rule), contents kept in the dropped item, content preview on look via actionbar instead of CrateOverlays; the cube block already exists (PAR-DECO-12)."
  - "Buoys: 16 dyed buoys (placed on water, float, hold a lead/anchor like TFC's BuoyBlockEntity)."
  - "Cakes: tfp:cake with bites 0-6 (TFCCakeBlock rules, food items), models of cake_slice1-6; candle cakes already exist (PAR-HEAT-26)."
tests:
  - "core: crate and cake rules; paper: crate listener tests"
done_criteria:
  - "crates store items, buoys float and cakes can be eaten with TFC's rules; checks in the showcase"
---

# PAR-DECO-29: Crate device, buoys and cake blocks (follow-up of PAR-DECO-24)


All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`. PAR-DECO-24 shipped `core/misc` CrateRules and CakeRules; PAR-DECO-12 shipped the crates and the palm mosaic as renderer cubes (`dev.tfp.datagen.misc.MiscCubes`, `configuration/misc/cubes.yml`); `BlockCoverage` names this item as owner of what is left.
