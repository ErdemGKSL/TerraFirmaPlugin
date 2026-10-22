---
id: PAR-METAL-15
title: "Blast furnace, tuyere and steel path"
area: METAL
status: open
wave: 36
deps: [PAR-METAL-14, PAR-METAL-12]
size: M
aliases: ["M4-BLAST-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BlastFurnaceBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/BlastFurnaceScreen.java"
  - "src/generated/resources/data/tfc/recipe/blast_furnace"
files_owned:
  - "core/src/main/java/dev/tfp/core/blastfurnace/**"
  - "core/src/test/java/dev/tfp/core/blastfurnace/**"
  - "core/src/test/resources/dev/tfp/core/blastfurnace/**"
  - "paper/src/main/java/dev/tfp/paper/blastfurnace/**"
  - "paper/src/test/java/dev/tfp/paper/blastfurnace/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/blastfurnace/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/blastfurnace/**"
  - "content/generated/content/craftengine/tfp/configuration/blastfurnace/**"
ui: true
in_game_steps:
  - "build the furnace; add ore/flux/charcoal; push bellows; screenshot GUI"
plan:
  - "Multiblock of sheet-metal-covered fire bricks scanned async; tuyere slot; bellows air; lazy form decided (TFC onCalendarUpdate burns fuel 1x: document); pig iron drips into a crucible below; GUI with tanks."
tests:
  - "core: BlastFurnaceRulesTest exists"
done_criteria:
  - "Pig iron produced into a crucible; steel via anvil working of pig iron (metal run self-test)"
---

# PAR-METAL-15: Blast furnace, tuyere and steel path

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
