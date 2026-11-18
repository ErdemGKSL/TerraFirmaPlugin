---
id: PAR-HEAT-36
title: "Firepit carrier culls the top face of the block below: the floor shows through the gaps of TFC's firepit model"
area: HEAT
status: open
wave: 17
deps: [PAR-HEAT-35]
size: S
aliases: ["follow-up of PAR-HEAT-35 (in-game check 2026-11-18; already visible in the PAR-HEAT-23 crops)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java"
  - "src/main/resources/assets/tfc/models/block/firepit_unlit.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Firepit.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/FirepitTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/device/DeviceSpecParityTest.java"
  - "docs/verification/PAR-HEAT-36/**"
ui: true
in_game_steps:
  - "op Bot; on a stone floor with grass, water and a cave a few blocks below, tfp debug place tfp:firepit[lit=false] and [lit=true], both axes"
  - "from 1.5 blocks and from above: no grass, water or cave colour shows between the rocks of the model; screenshots GUI 2 and 3"
plan:
  - "Seen in PAR-HEAT-35 (docs/verification/PAR-HEAT-35/gui2-close-unlit-x-unlit-z-lit-x.png, gui3-firepits.png): the carrier daylight_detector[inverted=false,power=0] has a 6 px slab occlusion shape that covers the whole bottom face, so the client culls the top face of the block under the firepit; the carrier is drawn empty and TFC's model (an item display) leaves gaps between its rocks and around the 12x12 coal plate, through which the blocks below show (x-ray: grass, water, shafts). PAR-HEAT-23's model carriers had the same cull."
  - "Options: (a) a hitbox carrier with the firepit outline whose occlusion shape does not cover the full bottom face (check VanillaCandidates rows for a non-occluding 16x6x16 or close outline; carrier policy and hide rules as usual); (b) keep the detector and give the item display a bottom element that draws the floor texture of the block below (not possible per block with one model: reject unless (a) fails); (c) record it as a divergence if no carrier exists. Prefer (a)."
tests:
  - "datagen: the chosen firepit carrier does not occlude the face below (a new flag in the candidate dump or a pinned list), still one shared hitbox-only carrier"
done_criteria:
  - "In game nothing below shows through a placed firepit on both axes, lit and unlit (screenshots GUI 2 and 3, docs/verification/PAR-HEAT-36/)"
---

# PAR-HEAT-36: Firepit carrier culls the floor's top face

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
