---
id: PAR-MECH-02
title: "Rotation visuals: spinning axles and gears"
area: MECH
status: open
wave: 50
deps: [PAR-MECH-01]
size: M
aliases: ["M6-MECH-02 (visuals)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/render"
  - "src/main/resources/assets/tfc/models/block/axle.json"
files_owned:
  - "integration/craftengine/src/main/java/dev/tfp/integration/craftengine/rotation/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/rotationvisual/**"
  - "core/src/main/java/dev/tfp/core/machine/RotationAngles*"
  - "core/src/test/java/dev/tfp/core/machine/RotationAngles*"
ui: true
in_game_steps:
  - "the MECH-01 setup; record 10 frames"
plan:
  - "entity_renderer item displays rotated with interpolation by the network speed, updated only on speed change (client interpolation keeps spinning) per craftengine-gui-furniture; no per-tick packets."
tests:
  - "integration: rotation packet count per speed change"
done_criteria:
  - "Axles visibly spin at the right direction"
---

# PAR-MECH-02: Rotation visuals: spinning axles and gears

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
