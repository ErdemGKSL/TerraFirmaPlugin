---
id: PAR-HEAT-35
title: "Firepit drawn by entity_renderer item displays on one hidden daylight detector hitbox carrier (instead of four model carrier states)"
area: HEAT
status: open
wave: 16
deps: [PAR-HEAT-24]
size: M
aliases: ["owner decision 2026-11-18: free three of the four daylight_detector states PAR-HEAT-23 took for the firepit models"]
tfc_refs:
  - "src/main/resources/assets/tfc/blockstates/firepit.json"
  - "src/main/resources/assets/tfc/models/block/firepit_lit.json"
  - "src/main/resources/assets/tfc/models/block/firepit_unlit.json"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/Firepit.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/device/DeviceSpecs.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/FirepitTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/device/DeviceSpecParityTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/HitboxAllocationTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/HitboxCarrierTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/CarrierPolicyRecipeTest.java"
  - "content/generated/content/craftengine/tfp/configuration/devices/firepit.yml"
  - "content/generated/content/craftengine/tfp/devices/firepit.yml"
  - "docs/verification/PAR-HEAT-35/**"
ui: true
in_game_steps:
  - "op Bot; weather clear 1000000; gamemode survival Bot; tfp debug thirst Bot 100; tfp debug hunger Bot 20"
  - "tfp debug place tfp:firepit[lit=false,axis=x] and tfp:firepit[lit=false,axis=z] on solid blocks, and lit=true of both axes next to them: screenshots at GUI scale 2 and 3 of the four looks (TFC's lit and unlit models, axis z turned 90), selection outline of the carrier"
  - "F3: block light 15 next to a lit firepit, 0 at an unlit one (night or a dark room)"
  - "light an unlit firepit with fuel (stick bundles, firestarter while sneaking): the display morphs to the lit model at once; right-click the lit firepit: the cooking screen opens"
  - "break a firepit: wood ash drops, the display is gone"
  - "relog; tp 300 blocks away and back (chunk reload): every display is back, none doubled"
  - "a lit fuelled firepit, tfp time add days 4, chunk reload: unlit look (PAR-HEAT-33)"
  - "walk 48+ blocks away and back: no leftover or missing display"
  - "30 firepits in view: count the display entities the client sees (F3 E), note TPS/MSPT and client FPS"
  - "a real daylight_detector (power 0 and the carrier's neighbours) still looks like a daylight detector"
plan:
  - "Today (PAR-HEAT-23) the firepit takes four daylight_detector states (power 0/10/12/14, hidden as 1/11/13/15), each with TFC's lit or unlit model, the z pair turned y 90. Replace them by ONE hitbox carrier (one daylight_detector state, hidden from real detectors the same way) drawn empty, and draw the look with an entity_renderer item display of a display-only item per lit value (tfp:render/firepit_lit / _unlit, TFC's model), turned per axis, exactly as the grill does since PAR-HEAT-24 (DeviceSpecs.firepitRendered, template tfp:block/device_rendered). Each (lit, axis) is its own appearance on the same carrier, so a state change morphs the display."
  - "HitboxDemands: the four firepit demands become TRANSPARENT (hitbox-only) and share one state. Firepit.firepitSpec: firepitRendered with the agreed carrier, its hide mapping, render items, rotation 180 - y (grill convention). Keep TFC settings, loot and luminance 15/0 per variant."
  - "Grill: already one hitbox-only carrier (soul_campfire) with four rendered appearances (PAR-HEAT-24), nothing to do. Pot: two VANILLA_LOOK placeholder demands, not loaded and not the four-model pattern: no change here (its model look belongs to the pot loading item)."
  - "Stop rule (owner): if the entity look shows blocking problems in game (flicker, missing display on chunk load, packet cost), keep the four-state models and record the evidence in docs/verification/PAR-HEAT-35/verdict.md."
tests:
  - "datagen: the four firepit appearances share one hitbox-only daylight_detector carrier with its hide mapping; each draws the render item of its lit value turned per axis; the written yml uses tfp:block/device_rendered and carries luminance 15/0 and TFC loot (FirepitTest, GeneratedCarrierTest, DeviceSpecParityTest, HitboxAllocationTest)"
done_criteria:
  - "The firepit uses one vanilla carrier state; three daylight_detector states are free again (test)"
  - "In game the lit and unlit firepit show TFC's models on both axes, light 15/0, the display survives relog, chunk reload, distance and a calendar skip, break removes it and drops wood ash, the cooking screen opens (screenshots GUI 2 and 3, docs/verification/PAR-HEAT-35/)"
---

# PAR-HEAT-35: Firepit drawn by entity_renderer on one hidden carrier

> Claimed: session_018kkWhFt2SBQHZwsVQ8uVdY, 2026-11-18

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
