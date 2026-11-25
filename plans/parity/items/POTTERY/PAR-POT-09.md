---
id: PAR-POT-09
title: "Bowls and powder bowls (wooden and ceramic bowl blocks)"
area: POTTERY
status: done
wave: 27
deps: [PAR-INFRA-02, PAR-INFRA-13]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/BowlBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BowlBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/component/Bowl.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/bowl/**"
  - "core/src/test/java/dev/tfp/core/bowl/**"
  - "core/src/test/resources/dev/tfp/core/bowl/**"
  - "paper/src/main/java/dev/tfp/paper/bowl/**"
  - "paper/src/test/java/dev/tfp/paper/bowl/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/bowl/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/bowl/**"
  - "content/generated/content/craftengine/tfp/configuration/bowl/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/bowl"
  - "place; add salt powder; screenshot"
plan:
  - "Placed bowls hold powders (for salting, crafting) as TFC; bowl item component for soups/salads."
  - "Done: PAR-POT-09 bowls: core/bowl BowlRules (TFC useItemOn order: glass, take 1 or 16, insert, salt; limit 16; powder texture and height), paper/bowl (device kinds tfp:wooden_bowl and tfp:ceramic/bowl with one slot of 16 powders, vanilla bowl places the wooden bowl, BowlSupport floor rule, powder layer as one item display, BowlGlass hook for PAR-PROC-16, BowlFood salting), datagen BowlBlocks (two CE blocks on model carriers of tfc:ceramic/bowl, TFC models and textures, loot, ceramic block item, 36 powder layer models and render items) and two HitboxDemands rows. Tests: core BowlRulesTest 10; paper BowlDevicesMockBukkitTest 15; datagen BowlBlocksTest 7. In-game check pending (container cannot run client): docs/verification/PAR-POT-09/verdict.md keeps the steps. Follow-up PAR-POT-15 (hopper automation)."
tests:
  - "paper: place/take"
done_criteria:
  - "A ceramic bowl placed holds salt"
---

# PAR-POT-09: Bowls and powder bowls (wooden and ceramic bowl blocks)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
