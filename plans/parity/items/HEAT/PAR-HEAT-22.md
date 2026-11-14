---
id: PAR-HEAT-22
title: "Dropped torches (go out in water, light leaves, thatch, log piles and pit kilns) and the torchTicks config key"
area: HEAT
status: open
wave: 15
deps: [PAR-HEAT-10]
size: S
aliases: ["follow-up of PAR-HEAT-10 (TorchItem.onEntityItemUpdate; ServerConfig torchTicks)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TorchItem.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
  - "src/generated/resources/data/tfc/tags/block/lit_by_dropped_torch.json"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/light/**"
  - "paper/src/test/java/dev/tfp/paper/light/**"
  - "core/src/main/java/dev/tfp/core/config/**"
  - "paper/src/main/java/dev/tfp/paper/config/**"
  - "paper/src/main/resources/config.yml"
ui: false
in_game_steps: []
plan:
  - "Current: core/light TorchRules has TFC's dropped-torch constants (extinguished batch count > 5 ? 1 + nextInt(5) : count, ages 20 / 160, chance 0.01); paper/light reads torchTicks from the constant TorchModule.TORCH_TICKS (86,400)."
  - "Dropped torch items, lazily (no per-entity ticking, as HotItemListener does for hot items): in a fire-extinguishing fluid, the torches go out in TFC's batches, each rolling the torch's loot table with burnt_out (LootService, LootContext burnt_out) and the extinguish sound; on a #tfc:lit_by_dropped_torch block (inside after 160 ticks, below after 20), TFC's 1 % per tick chance to start a STRONG fire there through FireStarting and kill the item (replay the elapsed ticks' draws at evaluation). TFC's lava particles stay off (particles only on explicit actions)."
  - "The torchTicks config key (TFC ServerConfig torchTicks, -1 disables) in config.yml and TfpConfig, read on /tfp reload, passed to TorchModule's suppliers."
tests:
  - "paper: MockBukkit dropped torches in water go out in batches and drop burnt-out loot; a dropped torch on leaves lights them"
  - "core/paper: torchTicks from config, -1 never burns out"
done_criteria:
  - "Dropped torch behaviour and the config key as TFC, recorded in the ledger"
---

# PAR-HEAT-22: Dropped torches and the torchTicks config key

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
