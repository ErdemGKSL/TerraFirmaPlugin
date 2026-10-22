---
id: PAR-TOOL-01
title: "Stone tools: rock tool heads and tools as CE items, crafting from knapped heads"
area: TOOL
status: open
wave: 19
deps: []
size: M
aliases: ["M3-TOOL-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rock/RockCategory.java"
  - "src/main/java/net/dries007/tfc/common/items/TFCItems.java"
  - "src/main/resources/assets/tfc/textures/item/stone"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/tool/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/tool/**"
  - "content/generated/content/craftengine/tfp/configuration/tool/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:stone/axe/igneous_intrusive"
  - "break a TFC log; time it"
plan:
  - "Generate the 4 rock categories x (axe, hammer, hoe, javelin, knife, shovel) heads and tools as CE items with tool components (speed and uses from TFC rock tiers) and mineable tags; names and models from TFC."
tests:
  - "tools/datagen: counts and tier numbers vs RockCategory/TFCTiers"
done_criteria:
  - "`ce item give Bot tfp:stone/axe/igneous_intrusive` mines logs at the TFC speed"
---

# PAR-TOOL-01: Stone tools: rock tool heads and tools as CE items, crafting from knapped heads

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
