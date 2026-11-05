---
id: PAR-PROC-23
title: "Material items in-game verification: ce item give for a sample of 20, names, textures, stack sizes"
area: PROC
status: done
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
  - "Re-review 2026-11-05: the in-game reject for tfp:gem_saw is PAR-PROC-24 (datagen tool rules naming tfp:mineable/{glass_saw,knife,hammer,scythe}, outside files_owned); ledger gem-saw row marked not buildable until PAR-PROC-24; the requested guards (self-test giving every tool item, datagen check of tool-rule tags) are follow-up PAR-PROC-27; the unverified hand split, real-use durability and slot-visible hover crops went into PAR-PROC-26 in_game_steps."
  - "Done: in-game check on Paper 1.21.11 + CE 26.9.2 with a 1.21.11 client at GUI scale 2 and 3: ce item give, TFC names and textures pass for 21 of the 22 listed ids (the criterion's sample is 20; the gem saw is one of the two extra ids), glue stacks to 32 and halite to 16, handstone/sandpaper show the durability bar once damaged. tfp:gem_saw cannot be built (PAR-PROC-24). Finding: fresh durability items lack minecraft:damage, so vanilla damage paths skip them (divergence row, follow-up PAR-PROC-26, which also takes the gem saw check). Evidence: docs/verification/PAR-PROC-19/ (verdict.md, 6 crops). Run by the worker session (no cheap-model agent available here)."
tests: []
done_criteria:
  - "`ce item give` works for the sampled 20 with TFC textures and names"
  - "Stack sizes follow TFC item_size (glue 32, halite 16)"
---

# PAR-PROC-23: Material items in-game verification: ce item give for a sample of 20, names, textures, stack sizes

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
