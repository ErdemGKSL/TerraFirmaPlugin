---
id: PAR-TOOL-11
title: "Obsidian tools: obsidian axe, hammer, hoe, javelin, knife and shovel with their heads, knapped from obsidian shards"
area: TOOL
status: open
wave: 24
deps: [PAR-TOOL-01, PAR-TOOL-05, PAR-WORLD-09]
size: S
aliases: ["parity audit 2026-11-06: TFCTiers.OBSIDIAN tools absent from every item (only the macuahuitl is in PAR-TOOL-04)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/TFCTiers.java"
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/generated/resources/data/tfc/recipe/knapping"
  - "src/generated/resources/data/tfc/recipe/crafting"
  - "src/main/resources/assets/tfc/textures/item"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/tool/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/tool/**"
  - "content/generated/content/craftengine/tfp/configuration/tool/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:groundcover/obsidian_shard 2; open the rock knapping screen with an obsidian shard and knap the axe head pattern; screenshot the result slot"
  - "ce item give Bot tfp:obsidian_axe; break a TFC log and time it against the TFC obsidian tier speed; screenshot the tooltip (durability 60)"
  - "ce item give Bot tfp:obsidian_javelin; throw it at a zombie; screenshot the hit"
plan:
  - "Current (audit 2026-11-06): TFC 1.21 adds an obsidian tool set (TFCTiers.OBSIDIAN: level 0, 60 uses, speed 5.0, attack bonus 2.5, enchantability 8): obsidian_axe/hammer/hoe/javelin/knife/shovel and their *_head items, knapped from `tfc:groundcover/obsidian_shard` with the rock knapping type (10 knapping recipes) and joined to sticks by crafting recipes. PAR-TOOL-01 covers the four rock categories only; PAR-TOOL-04 has the macuahuitl only."
  - "Extend the stone tool generator (same package as PAR-TOOL-01) with the obsidian tier: the 6 heads and 6 tools as CE items with TFC names, textures and models, tool components (speed and uses from TFCTiers.OBSIDIAN, mineable tags as the rock tools), attack attributes from ToolItem.productAttributes with TFC's per-tool factors, item_size/item_heat data from TFC."
  - "The knapping and crafting recipes come from the TFC tree through the existing recipe pipelines (PAR-PROC-01 knapping, PAR-PROC-02/03 crafting); check that the obsidian ids resolve and are not filtered out."
  - "The obsidian javelin uses PAR-TOOL-05's javelin runtime with the obsidian tier damage."
tests:
  - "tools/datagen: 12 obsidian items with tier numbers equal to TFCTiers.OBSIDIAN and attack values equal to TFC's productAttributes"
  - "tools/datagen: every obsidian knapping and crafting recipe output resolves to a shipped CE item"
done_criteria:
  - "The 12 obsidian items exist, knap and craft as in TFC, mine and hit at TFC's numbers; screenshots pass"
---

# PAR-TOOL-11: Obsidian tools: obsidian axe, hammer, hoe, javelin, knife and shovel with their heads, knapped from obsidian shards

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
