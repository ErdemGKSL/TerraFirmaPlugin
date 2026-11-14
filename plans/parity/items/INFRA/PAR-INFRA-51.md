---
id: PAR-INFRA-51
title: "Held snow layer ghost on snow-layer carriers: the client predicts snow[n+1] on the forge, quern handstone, grill and piles"
area: INFRA
status: open
wave: 15
deps: []
size: M
aliases: ["follow-up of PAR-INFRA-23 (in-game run 2026-11-14, docs/verification/PAR-INFRA-13/verdict.md, section Client prediction)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/QuernBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxCarrier.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/carrier-policy.json"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/class-overrides.json"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/HitboxCarrierTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/HitboxAllocationTest.java"
  - "docs/verification/PAR-INFRA-51/**"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11, then 26.3 (docs/testing/headless.md; debug entry looking_at_block always on): tfp time set noon; stone row; tfp debug place tfp:charcoal_forge[heat_level=0] and tfp:quern[has_handstone=true] (and tfp:grill once PAR-HEAT-08 loads it)"
  - "Bot holds minecraft:snow, looks at the top face (pitch 22 from 2 blocks), right-clicks once; record 2 s at 20 fps (ffmpeg x11grab); no frame may show a white snow block, another carrier's model or a changed outline"
  - "Bot holds cobblestone and right-clicks the forge's top face: note whether a ghost cobblestone appears for a frame (decision below)"
  - "26.x: Bot holds an iron hoe and right-clicks the top face of tfp:bellows[facing=north] (dirt_path); record 2 s at 20 fps; no frame may show the composter or any other carrier's model"
plan:
  - "Owner of PAR-INFRA-23's 'no ghost prediction with a held snow layer' criterion (2026-11-14, PROTOCOL 6.5): PAR-INFRA-23 is done with that criterion recorded as failing and tracked here; this item does not depend on PAR-INFRA-23. Second run (docs/verification/PAR-INFRA-13/verdict.md, Client prediction): with the crosshair on the forge's top face the flash lasts 4 frames (200 ms) on 1.21.11 and 2 frames on 26.3; an empty-hand frame afterwards shows the forge as snow layers 7 and the floor in front as stone, and the server has air above and in front, so it is the predicted snow[8] at the forge's own position."
  - "Same class of fault found in that run: on 26.3 (not 1.21.11) an iron hoe on the bellows (dirt_path) makes the client predict the till to farmland[moisture=0], which is the model carrier of composter stages 6-7, so the bellows flashes as the composter for 2 frames; the server keeps the bellows. The selector rule must also cover tool-use predictions (hoe till dirt_path/grass/dirt -> farmland, shovel path, axe strip, ...) on each version family: no model-mode carrier may be the client-predicted result of another carrier's state under a vanilla item use."
  - "Current (PAR-INFRA-23 run, 2026-11-14, both families): right-clicking the top face of a block on snow[layers=n<8] with a held vanilla snow layer makes the client predict snow[layers=n+1] for 50-150 ms. The forge (snow[7]) flashes as a white full snow block (the vanilla look of snow[8], its hide target); the quern with handstone (snow[6]) flashes as the charcoal forge, because snow[7] is the forge's model carrier. The server never changes the block and its resend restores it. The grill (snow[5], VANILLA mode) and every snow-layer pile carrier (charcoal, bloom, ingot piles) will do the same. unusable.json cannot express the fix: its rows only act on override classes (HitboxCarrier.select skips unusable entries of class-overrides lists only), and these classes are ranked."
  - "Decide with the owner and implement in the selector: (a) a policy rule that marks snow[layers=1..7] as 'predicted by a held snow layer' and excludes them for classes whose demand is not itself a snow-like layer pile (forge -> dirt_path or a CE pool, quern handstone -> next ranked), (b) let unusable.json rows act on ranked classes too (skip the carrier for that class), then add the forge and quern rows, or (c) accept the flash as a residual (a vanilla snow layer item is rarely held near devices) and record it as a divergence with no fix. Whatever the choice, a model-mode carrier must never be the n+1 prediction of another carrier (the quern-as-forge flash), so (c) still needs the quern moved off snow[6] or the forge off snow[7]."
  - "Second finding to decide in the same item: a held block item right-clicked on the forge's top face is predicted above it for about 2 frames and removed when the server's use handler takes the click (TFC opens the forge screen instead of placing). This is generic to every CE device whose use is consumed server-side (CraftEngine cannot tell the client the block is interactable); record it as a ledger residual if no fix exists."
  - "Rerun datagen, update the generated reports and the device YAML, add the ledger fallback row for every carrier that changes, and rerun the steps above."
tests:
  - "HitboxCarrierTest: the rule (or unusable row on a ranked class) moves the forge and the quern handstone off snow[layers<8] and no model-mode carrier is the n+1 snow state of another carrier, nor the till result (farmland[moisture=0]) of a carrier the 26.x client tills (dirt_path)"
  - "In-game frames in docs/verification/PAR-INFRA-51/"
done_criteria:
  - "No held-snow ghost (white block, other carrier's look or outline change) on the forge, quern and grill on 1.21.11 and 26.x (PAR-INFRA-23's moved criterion)"
  - "No composter (or other carrier) flash when a hoe is used on the bellows on 26.x"
  - "The decision on the block-item ghost on use-consuming devices is recorded in the ledger"
---

# PAR-INFRA-51: Held snow layer ghost on snow-layer carriers: the client predicts snow[n+1] on the forge, quern handstone, grill and piles

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
