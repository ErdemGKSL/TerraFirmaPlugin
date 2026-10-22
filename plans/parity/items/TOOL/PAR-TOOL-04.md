---
id: PAR-TOOL-04
title: "Swords, maces, macuahuitl: attack damage per TFC tier"
area: TOOL
status: open
wave: 20
deps: [PAR-TOOL-03]
size: S
aliases: ["gaps: sword and mace attack damage"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/Metal.java"
  - "src/main/java/net/dries007/tfc/common/items/TFCMaceItem.java"
  - "src/main/java/net/dries007/tfc/common/TFCTiers.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/weapon/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/weapon/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/sword/steel"
  - "summon minecraft:cow ~ ~ ~2 {NoAI:1b}"
  - "hit once; data get entity @e[type=cow,limit=1] Health"
plan:
  - "Generate swords (factor 1.0, speed -2.4) and maces (1.3, -3.4) for 9 metals and the obsidian macuahuitl with `attribute_modifiers` amount = TFC damage - 1 (fist base) as measured in plans/gaps.md; verify one tier in-game (cow, armour 0)."
tests:
  - "tools/datagen: amounts vs TFCTiers"
done_criteria:
  - "Steel sword hits an unarmoured cow for TFC damage ±0.05"
---

# PAR-TOOL-04: Swords, maces, macuahuitl: attack damage per TFC tier

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
