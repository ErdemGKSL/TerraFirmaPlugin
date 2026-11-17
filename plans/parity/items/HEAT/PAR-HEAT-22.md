---
id: PAR-HEAT-22
title: "Dropped torches (go out in water, light leaves, thatch, log piles and pit kilns) and the torchTicks config key"
area: HEAT
status: blocked
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
  - "BLOCKED (2026-11-17): the torchTicks config key needs edits to files outside files_owned. A new config section (or key) changes core/src/test/java/dev/tfp/core/config/ConfigCodecTest.java (emptyOrNullTreeGivesDefaults asserts hasSize(24) defaulted keys) and paper/src/test/java/dev/tfp/paper/config/ConfigServiceTest.java (the appended-sections list in containsExactly). To unblock: add both test files (core/src/test/java/dev/tfp/core/config/**, paper/src/test/java/dev/tfp/paper/config/**) to files_owned, then add the torch section (torch.torch-ticks, TFC default 86,400, range -1..Integer.MAX_VALUE) to TfpConfig/ConfigCodec/config.yml and pass () -> context.config().current().torch().torchTicks() to TorchDevice.Services and TorchService in TorchModule (read live, so /tfp reload applies it), with the core/paper '-1 never burns out' and 'from config' tests. Also still owed: the chunk-load catch-up test through the CatchUpService index (TorchModule.lazyBlocks)."
  - "Done so far (this commit): dropped torch items (DroppedTorches pure replay with TFC's draws, DroppedTorchListener with a 10-tick coarse check per torch item plus pickup, PaperDroppedTorchEffects: burnt_out loot per torch via LootService, extinguish sound, strong fire via FireStarting); the forward calendar skip re-check of loaded torches (CalendarSkipWatch + PaperTorchEffects.recheckAll, skip clause dropped from the ledger); one source for the default (test ties TorchModule.TORCH_TICKS, LightSourceRules.DEFAULT_TORCH_TICKS and datagen TorchBlocks.TORCH_TICKS). Tests: DroppedTorchMockBukkitTest 12, TorchMockBukkitTest +2 (18)."
  - "Current: core/light TorchRules has TFC's dropped-torch constants (extinguished batch count > 5 ? 1 + nextInt(5) : count, ages 20 / 160, chance 0.01); paper/light reads torchTicks from the constant TorchModule.TORCH_TICKS (86,400)."
  - "Dropped torch items, lazily (no per-entity ticking, as HotItemListener does for hot items): in a fire-extinguishing fluid, the torches go out in TFC's batches, each rolling the torch's loot table with burnt_out (LootService, LootContext burnt_out) and the extinguish sound; on a #tfc:lit_by_dropped_torch block (inside after 160 ticks, below after 20), TFC's 1 % per tick chance to start a STRONG fire there through FireStarting and kill the item (replay the elapsed ticks' draws at evaluation). TFC's lava particles stay off (particles only on explicit actions)."
  - "The torchTicks config key (TFC ServerConfig torchTicks, -1 disables) in config.yml and TfpConfig, read on /tfp reload, passed to TorchModule's suppliers."
  - "One source for the default: the config default and datagen light/TorchBlocks.TORCH_TICKS both match core LightSourceRules.DEFAULT_TORCH_TICKS (a test ties them)."
  - "Calendar skips: after a forward skip (CalendarService.skip, /tfp time add, sleep) re-check the tracked loaded torches against the calendar (a skip listener or a 20-tick global check as charcoal PitClock does), so they swap as soon as TFC's random ticks would; then drop the skip clause from the ledger row."
tests:
  - "paper: MockBukkit dropped torches in water go out in batches and drop burnt-out loot; a dropped torch on leaves lights them"
  - "core/paper: torchTicks from config, -1 never burns out"
  - "paper: MockBukkit forward calendar skip past the burn time swaps a loaded torch without a click or chunk reload; chunk-load catch-up driven through the CatchUpService index (TorchModule.lazyBlocks)"
done_criteria:
  - "Dropped torch behaviour and the config key as TFC, recorded in the ledger"
---

# PAR-HEAT-22: Dropped torches and the torchTicks config key

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
