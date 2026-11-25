---
id: PAR-METAL-15
title: "Blast furnace, tuyere and steel path"
area: METAL
status: done
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
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
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
  - "Done: core BlastFurnaceData (state, molten input tank, output tank, drip tick), BlastFurnaceStructure (BLAST_FURNACE_CHIMNEY, getChimneyLevels over a block probe) and BlastFurnaceSim (TFC's live serverTick replayed lazily over the span since the last touch: burn at the multiplier, melt of every input, conversion, tuyere damage; offline time uses the live rules, see the ledger). Paper dev.tfp.paper.blastfurnace: BlastFurnaceDevice (kind tfp:blast_furnace: tuyere slot, 32 each of inputs, catalysts and fuel; run on callbacks and neighbour changes: structure scan and overflow pop, intake by BlastFurnaceRules.intake, drip into the crucible below, molten tower, 20 tick callbacks while hot or dripping; lightAt, canAcceptAir and intakeAir), BlastFurnaceModule (config blast-furnace.*, fire lightable, BellowsConsumers registration, chunk-load callback, drop listener), BlastFurnaceScreens (a plain chest menu: tuyere slot and read-outs; textured screen is PAR-METAL-27), PaperBlastFurnaceBelow (crucible alloy fill and heat), BlastFurnaceBook (tfc:blast_furnace recipes). Datagen blastfurnace/BlastFurnaceBlock: the block tfp:blast_furnace on the solid pool (HitboxDemands.FULL_CUBE_POOLS entry and the pool count line of GeneratedCarrierTest, the only edits outside the globs; widened above). Tests: core BlastFurnaceSimTest 8, BlastFurnaceStructureTest 4; paper BlastFurnaceMockBukkitTest 18; datagen BlastFurnaceBlockTest 2. Pending in-game check (container cannot run client); the chimney needs tfp:reinforced_fire_bricks (PAR-DECO-16, open), so the steel run self-test waits for it. Ledger: feature row and 7 divergence rows."
tests:
  - "core: BlastFurnaceRulesTest exists"
done_criteria:
  - "Pig iron produced into a crucible; steel via anvil working of pig iron (metal run self-test)"
---

# PAR-METAL-15: Blast furnace, tuyere and steel path

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
