---
id: PAR-PROC-16
title: "Glassworking: blowpipe operations (blow, roll, stretch, pinch, flatten, saw)"
area: PROC
status: done
wave: 30
deps: [PAR-METAL-08]
size: M
aliases: ["M6-GLASS-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/BlowpipeItem.java"
  - "src/main/java/net/dries007/tfc/common/items/GlassBlowpipeItem.java"
  - "src/main/java/net/dries007/tfc/common/items/GlassworkingItem.java"
  - "src/main/java/net/dries007/tfc/common/component/glass/GlassWorking.java"
  - "src/generated/resources/data/tfc/recipe/glassworking"
  - "src/main/java/net/dries007/tfc/client/GlassblowingOverlays.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/glass/**"
  - "core/src/test/java/dev/tfp/core/glass/**"
  - "core/src/test/resources/dev/tfp/core/glass/**"
  - "paper/src/main/java/dev/tfp/paper/glass/**"
  - "paper/src/test/java/dev/tfp/paper/glass/**"
ui: false
in_game_steps:
  - "ce item give Bot tfp:blowpipe_with_glass (debug heat 1200)  (moved to PAR-PROC-52)"
  - "offhand jacks; use; watch actionbar  (moved to PAR-PROC-52)"
plan:
  - "Done: core `glass/GlassBlowpipes` (the other hand's operation: empty hand blows, looking below -0.95 stretches, wool cloth/jacks/paddle/gem saw; blowpipe transform, break chance 0.1 for clay, 0 for metal; `finish` = apply, recipe match, one break roll per finish); paper `glass/GlassPdc` (the `tfp:glass` record the tooltip reads), `GlassWork` (use with a hot-enough check, cooldown 20 ticks, steps on the action bar, result given and blowpipe emptied or broken, a batch right-clicked onto a single blowpipe), `GlassListener`, `GlassBowl` (provides the BowlGlass service of the powder bowl), `GlassModule` (listed before BowlModule; registers the tfc:add_glass and tfc:add_powder recipe modifiers). Tests: core GlassBlowpipesTest (9); paper GlassWorkMockBukkitTest (11). The pours onto a basin or table are PAR-PROC-17. The blowpipe, tool and batch items are not CraftEngine items yet: PAR-PROC-52."
  - "Blowpipe picks up a hot glass batch from a forge/crucible; holding the blowpipe with a glassworking tool in offhand and using applies the tool's operation (GlassRules) if the batch is hot; steps shown on the actionbar instead of TFC's overlay (divergence); finishing gives the result."
tests:
  - "paper: operation sequence completes a lamp glass recipe"
done_criteria:
  - "Making a lamp glass: blow, pinch, flatten, blow, saw gives it (paper GlassWorkMockBukkitTest, core GlassBlowpipesTest). Deferred check tracked in PAR-PROC-52 (in game, once the blowpipe, tool and batch items are CraftEngine items)"
---

# PAR-PROC-16: Glassworking: blowpipe operations (blow, roll, stretch, pinch, flatten, saw)


> see_not_parity: NP-UI-01, NP-CLIENT-03 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
