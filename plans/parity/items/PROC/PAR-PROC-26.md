---
id: PAR-PROC-26
title: "Durability material items carry minecraft:damage 0 (vanilla damageable); gem saw in-game check"
area: PROC
status: open
wave: 8
deps: [PAR-PROC-24]
size: S
aliases: ["follow-up of PAR-PROC-23 (found by the in-game check of PAR-PROC-19)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/main/java/net/dries007/tfc/common/items/GemSawItem.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/material/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/material/**"
  - "content/generated/content/craftengine/tfp/configuration/material/**"
  - "docs/verification/PAR-PROC-26/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:handstone, tfp:sandpaper, tfp:spindle, tfp:bone_needle, tfp:gem_saw; data get entity Bot Inventory[i].components: each has minecraft:damage 0 next to max_damage"
  - "item modify entity Bot hotbar.0 {\"function\":\"minecraft:set_damage\",\"damage\":0.5}: the durability bar appears (vanilla treats the item as damageable)"
  - "Hover the gem saw at GUI scale 2 and 3 with the cursor exactly on its slot: TFC texture and the name Gem Saw; the crop shows the hovered slot next to the tooltip; 8x crop of its icon. Recapture Mortar the same way (an earlier capture put its tooltip over the recipe-book button)"
  - "ce item give Bot every saw and tool item (tfp:gem_saw and each metal knife, hammer, scythe, saw): each arrives with its TFC name and texture"
  - "ce item give Bot tfp:glue 64 and tfp:ore/halite 64, then split the stacks by hand (right-click drag): glue never forms a stack above 32, halite never above 16"
  - "Real use: craft with the sandpaper as a damaged crafting remainder (and use the gem saw on glass): the durability bar appears and drops after each use, without set_components"
plan:
  - "Found 2026-11-05 by PAR-PROC-23 (docs/verification/PAR-PROC-19/verdict.md): MaterialItems emits max_damage for TFC's durability items (handstone 250, sandpaper 40, spindle, bone needle, ...) on a paper base, but no minecraft:damage component. Vanilla ItemStack.isDamageableItem() needs MAX_DAMAGE and DAMAGE, so set_damage, hurtAndBreak and Paper's ItemStack#damage do nothing on a fresh item. TFC's Properties.durability(n) sets both (DAMAGE 0). Emit damage 0 with max_damage (CE data key or raw component), regenerate, extend MaterialItemsTest."
  - "The gem saw could not be given in that check (PAR-PROC-24: tfp:mineable/glass_saw is not a server tag); once PAR-PROC-24 is done, check its name, texture and durability here."
tests:
  - "tools/datagen material: every item with max_damage also sets damage 0"
done_criteria:
  - "A freshly given handstone/sandpaper is damageable for vanilla (set_damage works, bar shows)"
  - "ce item give tfp:gem_saw works with TFC texture and name (GUI scale 2 and 3, crop shows the hovered slot next to the tooltip); gem saw durability bar after use"
  - "Real use without set_components: crafting with the sandpaper as a damaged remainder (and the gem saw on glass once glass exists) makes the durability bar appear and drop"
---

# PAR-PROC-26: Durability material items carry minecraft:damage 0 (vanilla damageable); gem saw in-game check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
