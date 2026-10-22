---
id: PAR-FAUNA-07
title: "Nest box: laying, hatching, GUI"
area: FAUNA
status: open
wave: 43
deps: [PAR-FAUNA-06, PAR-INFRA-03, PAR-INFRA-02]
size: M
aliases: ["M5-LIV-02 (nest box)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/NestBoxBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/NestBoxBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/NestBoxScreen.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/nestbox/**"
  - "core/src/test/java/dev/tfp/core/nestbox/**"
  - "core/src/test/resources/dev/tfp/core/nestbox/**"
  - "paper/src/main/java/dev/tfp/paper/nestbox/**"
  - "paper/src/test/java/dev/tfp/paper/nestbox/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/nestbox/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/nestbox/**"
  - "content/generated/content/craftengine/tfp/configuration/nestbox/**"
ui: true
in_game_steps:
  - "place nest box; spawn hen + rooster; tfp time add days 10; open GUI"
plan:
  - "Hens sit in a nest box (Seat-like), lay lazily (NestRules 30-tick grid), fertilized eggs hatch after the hatch days; 4-slot GUI."
tests:
  - "core: NestLayGoldenTest exists"
done_criteria:
  - "A fertilized egg hatches a chick in a nest box"
---

# PAR-FAUNA-07: Nest box: laying, hatching, GUI

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
