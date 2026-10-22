---
id: PAR-TOOL-02
title: "Tool mechanics on Paper: TFC mining speed, durability rules, forging efficiency"
area: TOOL
status: open
wave: 20
deps: [PAR-TOOL-01]
size: M
aliases: ["M3-TOOL-01", "M4-TOOL-02 (hooks)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/ToolItem.java"
  - "src/main/java/net/dries007/tfc/common/TFCTiers.java"
  - "src/main/java/net/dries007/tfc/common/component/forge/ForgingBonus.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/tool/**"
  - "core/src/test/java/dev/tfp/core/tool/**"
  - "core/src/test/resources/dev/tfp/core/tool/**"
  - "paper/src/main/java/dev/tfp/paper/tool/**"
  - "paper/src/test/java/dev/tfp/paper/tool/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/pickaxe/steel"
  - "mine raw granite 10x; compare times with tfp debug log"
plan:
  - "Current: core/tool rules golden-tested; metal tools generated with vanilla tool components."
  - "BlockDamageEvent/BlockBreakEvent: apply ToolRules.willConsumeDurability (stone axe/shovel vanilla rule), forging bonus efficiency (mining speed multiplier attribute on the item) and durability chance (unbreaking-like)."
  - "Shears special mining speed."
tests:
  - "core: ToolRulesGoldenTest exists"
  - "paper: MockBukkit durability consumption"
done_criteria:
  - "Mining time with a copper vs steel pickaxe matches TFC within 1 tick (measured)"
---

# PAR-TOOL-02: Tool mechanics on Paper: TFC mining speed, durability rules, forging efficiency

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
