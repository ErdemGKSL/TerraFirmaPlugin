---
id: PAR-INFRA-22
title: "Correct-tool gate for delegated block loot: CustomBlocks.isCorrectTool over CraftEngine's tool check"
area: INFRA
status: open
wave: 4
deps: [PAR-INFRA-09]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Rock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/integration/CustomBlocks.java"
  - "integration/craftengine/src/main/java/dev/tfp/integration/craftengine/CraftEngineIntegration.java"
  - "integration/craftengine/src/test/java/dev/tfp/integration/craftengine/CorrectToolTest.java"
  - "paper/src/main/java/dev/tfp/paper/loot/LootModule.java"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-INFRA-09's `LootBreakListener` takes a `LootBreakListener.CorrectTool` and, when it refuses the main-hand tool, turns the block's drops off and rolls nothing (TFC: `ServerPlayerGameMode` calls `playerDestroy`, and so the loot, only when `hasCorrectToolForDrops`). `LootModule` passes `CorrectTool.ANY`, so a block delegated to the runtime (no CE `loot:`, PAR-INFRA-21) drops its table with any tool: mining raw rock by hand would give 1-4 loose rocks where TFC gives nothing. CraftEngine enforces `correct_tools` / `required_break_power` only inside its own getDrops."
  - "Add `default boolean isCorrectTool(Block block, ItemStack tool)` to `CustomBlocks` (region thread; default true so `Absent` and the test fakes keep compiling) and implement it in `CraftEngineIntegration` over CraftEngine's own check (`BlockStateUtils.isCorrectTool` or the equivalent for the block's `ImmutableBlockState` and the tool's CE item, `require_break_power` / `correct_tools` settings). Wire it in `LootModule`: `new LootBreakListener(service, customBlocks, customBlocks::isCorrectTool)`."
  - "Blocks this covers in TFC (`requiresCorrectToolForDrops`): raw and hardened rock, cobble, bricks, smooth and spikes (Rock.java:130-140), ores, metal blocks, alabaster, halite. Remove the ledger divergence row that names this item when done."
tests:
  - "integration/craftengine: the adapter refuses an empty hand and accepts a correct pickaxe on a block with `correct_tools` (unit test over the CE check, or a self-test line if CE cannot load in a unit test)"
  - "paper: LootBreakMockBukkitTest already covers the listener side (anIncorrectToolDropsNothing)"
done_criteria:
  - "A CE block with `correct_tools` delegated to the loot runtime drops nothing when broken by hand and its TFC table with the correct tool"
---

# PAR-INFRA-22: Correct-tool gate for delegated block loot: CustomBlocks.isCorrectTool over CraftEngine's tool check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
