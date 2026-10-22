---
id: PAR-PROC-18
title: "Powderkeg"
area: PROC
status: open
wave: 14
deps: [PAR-INFRA-03, PAR-INFRA-02, PAR-INFRA-13]
size: M
aliases: ["M6-DECO-03 (powderkeg)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PowderkegBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/PowderkegBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/PowderkegScreen.java"
  - "src/main/java/net/dries007/tfc/util/PowderKegExplosion.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/powderkeg/**"
  - "core/src/test/java/dev/tfp/core/powderkeg/**"
  - "core/src/test/resources/dev/tfp/core/powderkeg/**"
  - "paper/src/main/java/dev/tfp/paper/powderkeg/**"
  - "paper/src/test/java/dev/tfp/paper/powderkeg/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/powderkeg/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/powderkeg/**"
  - "content/generated/content/craftengine/tfp/configuration/powderkeg/**"
ui: true
in_game_steps:
  - "place powderkeg; add gunpowder; seal; light; screenshot"
plan:
  - "12-slot GUI with seal; lit with fire -> fuse (powderKegFuseTime) -> explosion strength from gunpowder count (powderKegStrengthModifier), only natural blocks when configured; collapse trigger."
tests:
  - "core: strength golden"
done_criteria:
  - "A powderkeg explodes with TFC strength"
---

# PAR-PROC-18: Powderkeg

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
