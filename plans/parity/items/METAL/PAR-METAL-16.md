---
id: PAR-METAL-16
title: "Ingot and double-ingot piles"
area: METAL
status: open
wave: 36
deps: [PAR-INFRA-02, PAR-INFRA-08]
size: M
aliases: ["M4-INGOT-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/IngotPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/DoubleIngotPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/IngotPileBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/pile/**"
  - "core/src/test/java/dev/tfp/core/pile/**"
  - "core/src/test/resources/dev/tfp/core/pile/**"
  - "paper/src/main/java/dev/tfp/paper/pile/**"
  - "paper/src/test/java/dev/tfp/paper/pile/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/pile/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/pile/**"
  - "content/generated/content/craftengine/tfp/configuration/pile/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/ingot/copper 64"
  - "sneak-right-click the ground repeatedly; screenshot"
plan:
  - "Sneak-right-click with ingots builds piles (IngotPileRules add/remove climb); visuals via tint_source per metal and count layers; collision layer per count; break drops contents."
tests:
  - "core: IngotPileGoldenTest exists"
  - "paper: add/remove"
done_criteria:
  - "A 64-ingot pile of mixed metals renders and returns every ingot"
---

# PAR-METAL-16: Ingot and double-ingot piles

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
