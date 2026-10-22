---
id: PAR-POT-03
title: "Pit kiln: straw and log stages, lighting, lazy firing"
area: POTTERY
status: open
wave: 24
deps: [PAR-POT-01, PAR-POT-02, PAR-HEAT-05, PAR-HEAT-04, PAR-INFRA-13]
size: M
aliases: ["M3-POT-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/kiln/**"
  - "core/src/test/java/dev/tfp/core/kiln/**"
  - "core/src/test/resources/dev/tfp/core/kiln/**"
  - "paper/src/main/java/dev/tfp/paper/kiln/**"
  - "paper/src/test/java/dev/tfp/paper/kiln/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/kiln/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/kiln/**"
  - "content/generated/content/craftengine/tfp/configuration/kiln/**"
ui: true
in_game_steps:
  - "place unfired vessel (PAR-POT-01)"
  - "add 8 straw and 8 logs"
  - "light with firestarter"
  - "tfp time add hours 8"
  - "screenshot"
plan:
  - "Current: core/kiln rules (layers, progress, evaluate) golden-tested."
  - "Straw on a placed-item block converts it to a pit kiln (stages 0-7 straw, 8-15 logs); fire above lights it (FireLightable); evaluate lazily on interaction/chunk load; contents fired via HeatingService; failure when the structure becomes invalid (first invalid tick recorded)."
tests:
  - "paper: MockBukkit stage progression and lazy fire result"
done_criteria:
  - "Firing an unfired vessel in a pit kiln yields a vessel after pitKilnTicks (self-test `kiln.fire`)"
---

# PAR-POT-03: Pit kiln: straw and log stages, lighting, lazy firing

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
