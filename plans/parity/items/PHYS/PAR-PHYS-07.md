---
id: PAR-PHYS-07
title: "Collapse follow-ups: Folia stress self-test, TFC rock-slide sounds in the pack, collapse server config keys"
area: PHYS
status: open
wave: 18
deps: [PAR-PHYS-02]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/TFCSounds.java"
  - "src/main/resources/assets/tfc/sounds.json"
  - "src/main/resources/assets/tfc/sounds/random"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
  - "src/main/java/net/dries007/tfc/common/items/ChiselItem.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/CollapseStressCase.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/collapse/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/collapse/**"
  - "paper/src/main/java/dev/tfp/paper/collapse/**"
  - "paper/src/test/java/dev/tfp/paper/collapse/**"
ui: true
in_game_steps:
  - "dig a 5x5 room in raw rock without beams; mine ceiling blocks until a collapse starts"
  - "screenshot falling cobble"
plan:
  - "Left over by PAR-PHYS-02 (files it did not own). (1) itest `collapse.stress` on Folia: many simultaneous triggers across a region border (CollapseService.trigger on raw rock ceilings in two regions), assert every world read and write ran on its owner (ThreadAsserts), the service settles (isIdle) and no commit window exceeds the budget much. (2) Ship TFC's tfc:random.rock_slide_long, rock_slide_long_fake and rock_slide_short (and dirt_slide_short for PAR-PHYS-03) through datagen into the pack and play them from BukkitCollapseWorld.sound instead of the vanilla stone/gravel stand-ins. (3) TFC's collapse server config (collapseTriggerChance, collapseFakeTriggerChance, collapsePropagateChance, collapseMinRadius, collapseRadiusVariance, enableChiselsStartCollapses) as our config keys feeding CollapseRules' full constructor. (4) The in-game screenshot check of PAR-PHYS-02 (deferred there: the container could not run the client)."
tests:
  - "itest: Folia stress `collapse.stress`"
  - "datagen: the three rock-slide sound events in the pack"
done_criteria:
  - "Mining under unsupported raw rock causes a collapse in-game with TFC's sounds"
---

# PAR-PHYS-07: Collapse follow-ups: Folia stress self-test, TFC rock-slide sounds in the pack, collapse server config keys

> Claimed: parity worker session_011osUp9, 2026-11-25

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
