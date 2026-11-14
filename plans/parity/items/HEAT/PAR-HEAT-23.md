---
id: PAR-HEAT-23
title: "Firepit look in game: TFC's lit and unlit models on their own carriers (axis z turned 90), and the in-game firepit check"
area: HEAT
status: open
wave: 16
deps: [PAR-HEAT-19]
size: S
aliases: ["follow-up of PAR-HEAT-19 (the model appearances need hitbox demands of their own: HitboxDemands, DeviceSpecs and GeneratedCarrierTest are outside its files_owned; its in-game check moved here)"]
tfc_refs:
  - "src/main/resources/assets/tfc/blockstates/firepit.json"
  - "src/main/resources/assets/tfc/models/block/firepit_lit.json"
  - "src/main/resources/assets/tfc/models/block/firepit_unlit.json"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/FirepitBlockEntityRenderer.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/device/DeviceSpecs.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Firepit.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/FirepitTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/device/DeviceSpecParityTest.java"
  - "content/generated/content/craftengine/tfp/configuration/devices/firepit.yml"
  - "docs/verification/PAR-HEAT-06/**"
  - "docs/verification/PAR-HEAT-19/**"
ui: true
in_game_steps:
  - "op Bot (the `tfp debug` tree needs op and a player sender: run the debug commands from client chat; the device readout is `/tfp debug device` while looking at the block, not `/tfp device`)"
  - "tfp debug place tfp:firepit[lit=false] on a stone block; right-click a tfp:stick_bundle into it (the TFC logs `tfp:wood/log/*` are no CraftEngine items yet and a vanilla `minecraft:oak_log` is no TFC fuel; use a log once the wood family is loaded) and light it with a firestarter: the block shows TFC's lit model (rocks, hot coals, campfire flame), not a bare daylight_detector"
  - "right-click the lit firepit with `ce item give Bot tfp:food/beef`"
  - "tfp time add hours 1"
  - "tfp debug device (the input slot empty, cooked beef in output slot 5); right-click with an empty hand takes it"
  - "tfp time add hours 1; tfp debug device: read the ash and the temperature (block entity present, kind tfp:firepit)"
  - "with fuel and in rain: tfp debug device shows the burn going twice as fast and the firepit still lit (rain does not extinguish, per the ledger)"
  - "douse a warm lit firepit with a water bucket (bucket turns empty) and another with a shovel (one damage); both show the unlit model; a shovel on an unlit firepit with ash gives the wood ash first"
  - "place one with axis z (look along z when building it from a stick pile): the model is turned 90 degrees"
  - "Screenshots at GUI scale 2 and 3 of the lit and the unlit firepit, both axes (the firepit GUI itself is PAR-HEAT-07 / PAR-HEAT-20)"
plan:
  - "Current: PAR-HEAT-19 loads tfp:firepit (configuration/devices/firepit.yml, template tfp:block/device), copies TFC's firepit_lit and firepit_unlit models and their textures into the pack (AssetPipeline, `Firepit.models`), and runs self-test `firepit.cook`. The appearances `lit` and `unlit` still sit on the selector's vanilla-look carrier `daylight_detector[inverted=false,power=0]`, which the grill and the pot share, so no model can go on it (one vanilla state, one model)."
  - "Hitbox: replace the firepit's two VANILLA_LOOK demands in HitboxDemands with four model demands, (lit, axis) each with TFC's model (`tfp:block/firepit_lit` / `_unlit`; the axis-z pair needs its own carriers since a carrier state takes one model and CraftEngine cannot rotate an explicit-state model per variant). Check the selector gives four distinct daylight_detector (or equally shaped) states and whether those need a hide mapping (a real daylight detector must not show a firepit). Keep the grill and the pot on their placeholders. Pin the four in GeneratedCarrierTest."
  - "Spec: four appearances (`lit_x`, `lit_z`, `unlit_x`, `unlit_z`) chosen by lit and axis in DeviceSpecs.firepitFamily (or a firepit-only factory; the grill and the pot keep two), `model:` on each; the z ones use a rotated copy of TFC's model (y 90, written by datagen next to the copied one, never by hand). Firepit.VISUAL_PROPERTIES already names lit and axis."
  - "TFC draws the fuel logs (firepit_log_1..4 per fuel state) with FirepitBlockEntityRenderer; that is not part of this item: record it as a divergence (or a follow-up with an entity_renderer element) once the model look is in."
  - "Run the in-game steps above (headless client, PROTOCOL 6) and store the crops and verdict in docs/verification/PAR-HEAT-19/."
tests:
  - "datagen: the four firepit carriers are distinct MODEL assignments with TFC's models; the written appearances name them"
done_criteria:
  - "Lit firepit cooks raw beef in the expected TFC time in game (screenshot of the lit visual, docs/verification/PAR-HEAT-19/)"
  - "Lit and unlit firepits show TFC's models on both axes, no daylight detector look"
---

# PAR-HEAT-23: Firepit look in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
