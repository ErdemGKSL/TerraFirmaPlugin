---
id: PAR-PROC-52
title: "Glassworking items in CraftEngine and the in-game blowpipe check"
area: PROC
status: done
wave: 31
deps: [PAR-PROC-16]
size: M
aliases: ["moved from PAR-PROC-16 (2026-11-25, PROTOCOL 6.5): the blowpipes, jacks, paddle, wool cloth and the four glass batches are in the id map but have no CraftEngine item entry, so nothing ran on a real server"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/main/resources/assets/tfc/models/item/blowpipe.json"
  - "src/main/resources/assets/tfc/items/blowpipe_with_glass.json"
  - "src/main/java/net/dries007/tfc/common/items/BlowpipeItem.java"
files_owned:
  - "docs/verification/PAR-PROC-52/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/glass/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/glass/**"
  - "paper/src/main/java/dev/tfp/paper/glass/**"
  - "paper/src/test/java/dev/tfp/paper/glass/**"
  - "content/generated/content/craftengine/tfp/configuration/glass/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:blowpipe; hold tfp:silica_glass_batch on the cursor and right-click the blowpipe in the inventory: it becomes tfp:blowpipe_with_glass"
  - "heat it (tfp debug heat 1200 on the held item); with an empty off hand use: the blow sound plays, the action bar lists Blow; offhand tfp:jacks: Pinch; tfp:paddle: Flatten; empty hand: Blow; tfp:gem_saw: Saw; the lamp glass appears in the inventory and the blowpipe is empty again; a cold blowpipe shows Not hot enough"
  - "right-click a powder bowl block holding the blowpipe with glass: a soda ash step is added; screenshot the item tooltip (steps) and the action bar"
plan:
  - "Datagen `glass/GlassItems`: CE entries for tfp:blowpipe, tfp:ceramic_blowpipe, tfp:blowpipe_with_glass, tfp:ceramic_blowpipe_with_glass (TFC models and the heat-dependent glass looks), tfp:jacks, tfp:paddle, tfp:wool_cloth (the loom's output is a plain CE item today; make it carry the glassworking tool marker if needed), the four glass batch items, the blowpipe size/weight and the tags the code uses (tfc:glass_batches already exports). Give the items the right durability/stack size. Check the PAR-INFRA-49 coverage owners in ItemCoverage (PAR-PROC-16 lists them) and drop the owner lines once loaded."
  - "Then the in-game check and, if worthwhile, TFC's 40-tick hold with the spyglass use animation through a minecraft:consumable component on the glass blowpipe items (ledger row for PAR-PROC-16: today the operation is instant on the click)."
tests:
  - "datagen: the glass items load with TFC's ids, models and sizes"
  - "Screenshots + verdict.md in docs/verification/PAR-PROC-52/"
  - "Done: datagen `glass/GlassItems` (12 CE items: the four blowpipes, jacks, paddle, the four batches, lamp glass, lens; TFC textures and names; stack 1 for the tools, 64 otherwise), the ItemCoverage owner lines dropped; `GlassListener` gives the click to a device block (the powder bowl) first. In game (docs/verification/PAR-PROC-52/): batch loading, heat glow, Blow, Pinch, steps tooltip, Not hot enough, lamp glass made. The powder bowl does not write the soda ash step back to the blowpipe: PAR-PROC-55. The 40-tick hold was not done (ledger divergence). Tests: datagen GlassItemsTest 3, paper GlassWorkMockBukkitTest +1."
done_criteria:
  - "Making a lamp glass in game: load a batch, heat, blow, pinch, flatten, blow, saw; the action bar shows the steps"
---

# PAR-PROC-52


All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
