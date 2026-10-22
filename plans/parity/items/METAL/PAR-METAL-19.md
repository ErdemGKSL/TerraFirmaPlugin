---
id: PAR-METAL-19
title: "Metal lamps: fuel, light, lit state"
area: METAL
status: open
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
ui: true
in_game_steps:
  - "place tfp:metal/lamp/copper; fill olive oil; light"
  - "tfp time add days 10; screenshot"
plan:
  - "Fill with olive oil/tallow/lamp fuel fluids by bucket/jug (lampCapacity); lit lamps emit light 15 (CE luminance by lit state) and burn fuel lazily (LampRules, the TFC remainder drop per check); item keeps fuel when broken."
tests:
  - "core: LampFuelTsvGoldenTest exists"
  - "paper: catch-up"
done_criteria:
  - "A lit lamp goes out when its fuel runs out (time add)"
---

# PAR-METAL-19: Metal lamps: fuel, light, lit state

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
