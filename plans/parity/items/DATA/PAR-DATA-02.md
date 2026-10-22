---
id: PAR-DATA-02
title: "Advancements: triggers, awards and presentation"
area: DATA
status: open
wave: 34
deps: [PAR-INFRA-01]
size: M
aliases: ["M6-ADV-01"]
tfc_refs:
  - "src/main/resources/data/tfc/advancement"
  - "src/main/java/net/dries007/tfc/util/advancements/TFCAdvancements.java"
  - "src/main/java/net/dries007/tfc/util/advancements/GenericTrigger.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/advancement/**"
  - "core/src/test/java/dev/tfp/core/advancement/**"
  - "core/src/test/resources/dev/tfp/core/advancement/**"
  - "paper/src/main/java/dev/tfp/paper/advancement/**"
  - "paper/src/test/java/dev/tfp/paper/advancement/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/advancement/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/advancement/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Advancements.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/AdvancementsTest.java"
ui: true
in_game_steps:
  - "pick up a loose rock"
  - "screenshot toast; open advancements screen"
plan:
  - "Ship the advancements as datapack advancements with `minecraft:impossible` criteria granted by our trigger API (`Advancements.fire(player, criterionId)`), keeping TFC icons (CE items) and translations; vanilla toast shows them."
tests:
  - "tools/datagen: tree emitted as datapack"
  - "paper: fire grants"
done_criteria:
  - "Picking up a loose rock grants the first story advancement (toast screenshot)"
---

# PAR-DATA-02: Advancements: triggers, awards and presentation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
