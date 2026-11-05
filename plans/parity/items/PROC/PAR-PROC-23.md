---
id: PAR-PROC-23
title: "Material items in-game verification: ce item give for a sample of 20, names, textures, stack sizes"
area: PROC
status: open
wave: 6
deps: []
size: S
aliases: ["follow-up of PAR-PROC-19 (its in_game_steps and screenshot done-criterion)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
files_owned:
  - "docs/verification/PAR-PROC-19/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ore/rich_hematite"
  - "ce item give Bot tfp:gem/ruby"
  - "ce item give Bot tfp:powder/salt"
  - "ce item give Bot <each of>: tfp:ore/halite tfp:powder/flux tfp:jute_fiber tfp:burlap_cloth tfp:silk_cloth tfp:wool_cloth tfp:papyrus tfp:unrefined_paper tfp:glue tfp:soot tfp:mortar tfp:sandpaper tfp:straw tfp:stick_bunch tfp:blubber tfp:wool_yarn tfp:spawn_egg/cow tfp:handstone tfp:gem_saw"
  - "Screenshot the hotbar/inventory at GUI scale 2 and 3 with each item hovered: TFC texture (not a missing-texture cube), TFC English name (from assets/tfp_material/lang), handstone as TFC's 3D healthy handstone, durability bar on handstone/sandpaper/gem saw after use; 8x crop of three icons"
  - "ce item give Bot tfp:glue 64: the stack splits into 32s (TFC light); tfp:ore/halite 64: 16s"
plan:
  - "Deferred screenshot check of PAR-PROC-19: the PAR-PROC-19 worker session could not start the cheap-model agent that runs the headless server and client (PROTOCOL §6.1), so the check is a separate item. Store crops and verdict.md in docs/verification/PAR-PROC-19/."
tests: []
done_criteria:
  - "`ce item give` works for the sampled 20 with TFC textures and names"
  - "Stack sizes follow TFC item_size (glue 32, halite 16)"
---

# PAR-PROC-23: Material items in-game verification: ce item give for a sample of 20, names, textures, stack sizes

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
