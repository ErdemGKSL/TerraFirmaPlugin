---
id: PAR-DECO-14
title: "Thatch bed: sleeping and spawn setting"
area: DECO
status: open
wave: 49
deps: [PAR-POT-08]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/ThatchBedBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/ThatchBedBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/bed/**"
  - "core/src/test/java/dev/tfp/core/bed/**"
  - "core/src/test/resources/dev/tfp/core/bed/**"
  - "paper/src/main/java/dev/tfp/paper/bed/**"
  - "paper/src/test/java/dev/tfp/paper/bed/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/bed/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/bed/**"
  - "content/generated/content/craftengine/tfp/configuration/bed/**"
ui: true
in_game_steps:
  - "place thatch bed; sleep at night; screenshot"
plan:
  - "Two-block thatch bed (vanilla bed carrier with TFC look) with TFC's sleep/spawn rules and no sleeping in thunderstorms option."
tests:
  - "paper: config gates"
done_criteria:
  - "Sleeping in a thatch bed skips the night"
---

# PAR-DECO-14: Thatch bed: sleeping and spawn setting

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
