---
id: PAR-HEAT-12
title: "Firebox, stove and stove pot"
area: HEAT
status: open
wave: 16
deps: [PAR-HEAT-07]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/FireboxBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/StoveBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/StovePotBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/FireboxBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/FireboxScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/FireboxContainer.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/firebox/**"
  - "core/src/test/java/dev/tfp/core/firebox/**"
  - "core/src/test/resources/dev/tfp/core/firebox/**"
  - "paper/src/main/java/dev/tfp/paper/firebox/**"
  - "paper/src/test/java/dev/tfp/paper/firebox/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/firebox/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/firebox/**"
  - "content/generated/content/craftengine/tfp/configuration/firebox/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:firebox[lit=true]"
  - "right-click; screenshot GUI"
plan:
  - "Firebox heats the block above (stove, crucible, pot) through the IHeatConsumer interface; reuse FirepitRules for its burn; GUI per FireboxScreen; stove/stove pot blocks as TFC."
tests:
  - "core: firebox heat transfer golden"
done_criteria:
  - "Firebox under a crucible heats it (self-test)"
---

# PAR-HEAT-12: Firebox, stove and stove pot

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
