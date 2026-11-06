---
id: PAR-METAL-20
title: "Charcoal forge lit tops turned 180°: the heat_N item display must match TFC's block-model orientation"
area: METAL
status: done
wave: 5
deps: [PAR-INFRA-19]
size: S
aliases: ["PAR-METAL-10 follow-up (step 10)"]
tfc_refs:
  - "src/main/resources/assets/tfc/models/block/charcoal_forge/template_forge.json"
  - "src/main/resources/assets/tfc/blockstates/charcoal_forge.json"
  - "src/main/resources/assets/tfc/textures/block/devices/charcoal_forge"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/ForgeDevices.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/ForgeDevicesTest.java"
  - "docs/verification/charcoal-forge-orientation/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/device/DeviceSpecs.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/device/DeviceSpecParityTest.java"
ui: true
in_game_steps:
  - "tfp time set noon; time set 6000; tfp debug place tfp:charcoal_forge[heat_level=0], then [heat_level=7], looking straight down from 3 blocks above (yaw 0, pitch 90)"
  - "film the top (ffmpeg x11grab, 30/s) and match each capture's 16x16 texel centres against TFC's 0.png and 7.png frames under the 8 orientations"
plan:
  - "UNBLOCKED (manager, 2026-11-05): files_owned now includes DeviceSpecs.java and DeviceSpecParityTest.java (PAR-INFRA-19 is done, nobody else edits them). Make the half turn in the spec's render element and update the parity test to expect it."
  - "BLOCKED (2026-11-05): the fix cannot be made inside files_owned. The turn (`Renderer.rotated(\"0,180,0\")` on the seven lit-level displays, tried in ForgeDevices by rebuilding the spec from DeviceSpecs.charcoalForge) makes tools/datagen/src/test/java/dev/tfp/datagen/device/DeviceSpecParityTest.java (owned by PAR-INFRA-19) fail: deviceGeneratorsWriteTheSpecWithTfcValues builds DeviceSpecs.charcoalForge itself and requires configuration/charcoal_forge.yml to be byte-equal to it, without a rotation. The model route (turning the up face of the display-only heat_1..7 models) is also closed: those files are AssetPipeline's copies and OutputTree rejects a second, different output for a path. Unblock by widening files_owned to tools/datagen/src/main/java/dev/tfp/datagen/device/DeviceSpecs.java and DeviceSpecParityTest.java (add a rotation argument to DeviceSpecs.charcoalForge, applied as `.rotated(rotation)` on the overlay renderer next to `.scaled(overlayScale)`; ForgeDevices passes \"0,180,0\"; the parity test passes the same) or by a PAR-INFRA follow-up that does that. Other users of tfp:item/render displays: firepit, grill and pot have no entity_renderer yet; the PAR-INFRA-17 rendered cubes (only the S2 spike calls them) draw a uniform cube, and CrackedOverlays already accounts for the display's 180 degree turn."
  - "Found by PAR-METAL-10 step 10 (docs/verification/charcoal-forge/verdict.md, capture 14-lit-top-turned-180-vs-block-model.png): the level 0 top, drawn by the block model `heat_0` on `snow[layers=7]`, matches TFC's `0.png` turned 180° on screen, which is how vanilla (and TFC) draws an up face. The lit tops, drawn by the `entity_renderer` item display of `tfp:render/charcoal_forge/heat_N`, match `N.png` unturned: the display is turned 180° about the vertical axis relative to TFC's block model. The sides carry one texture on all four faces and look the same."
  - "Fix in ForgeDevices (after PAR-INFRA-19 moves it onto DeviceSpecs, in the spec's render element): turn the display element half a turn about Y (CE `rotation: \"0,180,0\"` on the element; a single number means -yaw), or give the render model a display transform with the turn, whichever keeps the 1.002 scale centred on the block. Pin it in ForgeDevicesTest."
  - "Check that no other device using `tfp:item/render` displays (firepit, grill, pot when their renderers land) has the same half turn; file items for any that do."
  - "Done: DeviceSpecs.charcoalForge takes an overlay rotation applied as `.rotated(...)` next to `.scaled(overlayScale)`; ForgeDevices passes OVERLAY_ROTATION \"0,180,0\", DeviceSpecParityTest passes the same; ForgeDevicesTest.theLitDisplaysAreTurnedHalfATurnToMatchTheBlockModel pins it on all 7 lit levels (none on level 0). In game: level 0 and level 7 tops both match TFC's texture turned 180 degrees from above (yaw 0), as the block model draws it (60 + 120 captures, correlation >= 0.9995): docs/verification/charcoal-forge-orientation/verdict.md. Firepit, grill and pot have no renderer yet, rendered cubes are uniform and CrackedOverlays already turns its quads: no follow-up item."
tests:
  - "tools/datagen: ForgeDevicesTest pins the rotation of the charcoal forge's render element"
  - "In game: the lit top matches TFC's N.png frames under the same orientation as the level 0 block model (correlation 1.0 in both)"
done_criteria:
  - "Lit and cold forge tops have TFC's block-model orientation; verdict in docs/verification/charcoal-forge-orientation/"
---

# PAR-METAL-20: Charcoal forge lit tops turned 180°: the heat_N item display must match TFC's block-model orientation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
