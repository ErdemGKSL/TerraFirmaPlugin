---
id: PAR-METAL-19
title: "Metal lamps: fuel, light, lit state"
area: METAL
status: done
wave: 48
deps: [PAR-DECO-09, PAR-INFRA-04, PAR-INFRA-11, PAR-INFRA-13]
size: M
aliases: ["M4-WEATH-01 (lamps)", "M3-HEAT-04 (lamps)", "M6-DECO-03 (lamps)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/LampBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/LampBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/items/LampBlockItem.java"
  - "src/generated/resources/data/tfc/tfc/lamp_fuel"
files_owned:
  - "core/src/main/java/dev/tfp/core/lamp/**"
  - "core/src/test/java/dev/tfp/core/lamp/**"
  - "core/src/test/resources/dev/tfp/core/lamp/**"
  - "paper/src/main/java/dev/tfp/paper/lamp/**"
  - "paper/src/test/java/dev/tfp/paper/lamp/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/lamp/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/lamp/**"
ui: true
in_game_steps:
  - "place tfp:metal/lamp/copper; fill olive oil; light"
  - "tfp time add days 10; screenshot"
plan:
  - "Fill with olive oil/tallow/lamp fuel fluids by bucket/jug (lampCapacity); lit lamps emit light 15 (CE luminance by lit state) and burn fuel lazily (LampRules, the TFC remainder drop per check); item keeps fuel when broken."
  - "Done: tfp:metal/lamp/<metal> for the nine ALL metals as CE blocks (hanging x lit, light 15 when lit, LampBlock outlines on shared hitbox-only carriers, one item display per state, TFC settings/loot/item/names; LampBlocks, files_owned widened to tools/datagen/.../lamp/** because the blocks need a generator), core LampFuel/LampData/LampRules (lazy checkHasRanOut with TFC's counter as carry), paper LampDevice/LampModule (nine SAVE kinds, container fill and empty, quench, fire-start light, lava self-light, run-dry callback, chunk-load update, support and placement guard); tests LampFuelTsvGoldenTest + LampRulesTest (core, 16), LampDeviceMockBukkitTest (paper, 12), LampBlocksTest (datagen, 6); ledger row and 5 divergences. Pending in-game check (container cannot run client): the in_game_steps stay in this file, see docs/verification/PAR-METAL-19/verdict.md."
tests:
  - "core: LampFuelTsvGoldenTest exists"
  - "paper: catch-up"
done_criteria:
  - "A lit lamp goes out when its fuel runs out (time add)"
---

# PAR-METAL-19: Metal lamps: fuel, light, lit state

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
