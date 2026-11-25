---
id: PAR-REL-13
title: "Metal run self-test: pig iron from a real blast furnace"
area: REL
status: open
wave: 54
deps: [PAR-REL-05, PAR-DECO-16, PAR-METAL-15]
size: S
aliases: ["follow-up of PAR-REL-05"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BlastFurnaceBlock.java"
  - "src/generated/resources/data/tfc/recipe/blast_furnace/pig_iron.json"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/MetalRunCase.java"
ui: false
in_game_steps: []
plan:
  - "PAR-REL-05's metal.run_rules case uses stock pig iron ingots: the tfp:blast_furnace chimney needs #tfc:blast_furnace_insulation (tfp:reinforced_fire_bricks), which PAR-DECO-16 (open) adds."
  - "Once it exists: in MetalRunCase build a one-level reinforced fire brick chimney on the tfp:blast_furnace, a tuyere in its slot, a crucible below; drop cast iron (melted from the bloomery's cast iron ingots or stock) and flux, light it, pass time with air from tfp:bellows or the calendar, drip pig iron into the crucible, cast a pig iron ingot and feed it to the existing steel and black steel steps in place of the stock ingots."
tests:
  - "itest"
done_criteria:
  - "metal.run_rules green on Paper and Folia 1.21.11 with the pig iron made in a real blast furnace"
---

# PAR-REL-13: Metal run self-test: pig iron from a real blast furnace

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
