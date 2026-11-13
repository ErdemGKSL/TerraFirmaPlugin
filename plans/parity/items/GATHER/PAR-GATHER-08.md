---
id: PAR-GATHER-08
title: "Axe stripping: TFC logs and woods to their stripped forms, golden bamboo block to stripped bamboo"
area: GATHER
status: open
wave: 22
deps: [PAR-WORLD-10, PAR-INFRA-02]
size: S
aliases: ["parity audit 2026-11-13: LogBlock/BambooBlock getToolModifiedState(AXE_STRIP) named by no item; PAR-WORLD-10 only generates the stripped blocks"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/LogBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java"
  - "src/main/java/net/dries007/tfc/common/blocks/BambooBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/strip/**"
  - "core/src/test/java/dev/tfp/core/strip/**"
  - "paper/src/main/java/dev/tfp/paper/strip/**"
  - "paper/src/test/java/dev/tfp/paper/strip/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/axe/copper; place tfp:wood/log/oak with axis x; right-click it with the axe; screenshot: tfp:wood/stripped_log/oak, same axis, axe durability -1, strip sound"
  - "Same for tfp:wood/wood/birch; then with a stone axe (PAR-TOOL-01 when present, else a vanilla stone axe) to show any #minecraft:axes item strips"
  - "Right-click a log with a non-axe item and while in adventure mode: nothing happens"
plan:
  - "Current (audit 2026-11-13): TFC strips logs and woods with any axe (LogBlock.getToolModifiedState for ItemAbilities.AXE_STRIP, keeping axis and BRANCH_DIRECTION via Helpers.copyProperties) and strips tfc:golden_bamboo_block to minecraft:stripped_bamboo_block (BambooBlock). Lightning stripping (PAR-CLIM-08) uses the same mapping. PAR-WORLD-10 generates the log and stripped blocks but no item gives them the interaction; vanilla's strip code only knows vanilla logs."
  - "core/strip: the TFC pair table (log -> stripped_log, wood -> stripped_wood for all 20 woods, golden bamboo block -> minecraft:stripped_bamboo_block) derived from TFC's Wood.BlockType and TFCBlocks, plus the property copy rule (axis; branch_direction where the stripped block has it)."
  - "paper/strip: a right-click handler on CE blocks in the table when the held item is in #minecraft:axes (vanilla and TFC axes, via the tag runtime): snapshot, decide, apply on the region thread through the commit queue with the vanilla strip sound, 1 durability on the axe (not in creative), arm swing; adventure mode follows vanilla's can-build rule. Prefer CraftEngine's built-in strippable_block where the block's generator can carry it (that YAML belongs to PAR-WORLD-10 / PAR-DECO-12); otherwise this handler is the implementation and the table is the single source for both. Expose the table as a service so PAR-CLIM-08 reuses it."
  - "The golden bamboo block row works as soon as PAR-DECO-12 generates the block; until then it is covered by the table test."
tests:
  - "core: table matches TFC for every wood and the bamboo block; property copy keeps axis and branch_direction"
  - "paper: MockBukkit: axe strips and loses 1 durability, creative does not lose durability, non-axe and adventure do nothing"
done_criteria:
  - "Every TFC log and wood strips with an axe as TFC (axis kept, sound, durability); the bamboo row is in the tested table; tests green; screenshots pass review"
---

# PAR-GATHER-08: Axe stripping: TFC logs and woods to their stripped forms, golden bamboo block to stripped bamboo

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
