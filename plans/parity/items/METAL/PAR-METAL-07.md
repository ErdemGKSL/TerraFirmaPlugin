---
id: PAR-METAL-07
title: "Charcoal forge visuals: TFC heat-level models on a snow-layer carrier"
area: METAL
status: done
wave: 0
deps: []
size: S
aliases: ["gaps: UI defect 4 (forge placeholder)", "M4-FORGE-01 (visual)"]
tfc_refs:
  - "src/main/resources/assets/tfc/models/block/charcoal_forge"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/ForgeDevices.java"
ui: false
in_game_steps: []
plan:
  - "Done in commit caf94f4 (snow[layers=7] carrier, entity_renderer heat_N displays, luminance heat*2; ledger divergence row)."
tests:
  - "ForgeDevicesTest"
done_criteria:
  - "Committed"
---

# PAR-METAL-07: Charcoal forge visuals: TFC heat-level models on a snow-layer carrier

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
