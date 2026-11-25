---
id: PAR-DATA-01
title: "Commands under /tfp: player, count block, clear world, add trim, weather hooks"
area: DATA
status: done
wave: 32
deps: [PAR-INFRA-01]
size: M
aliases: ["M6-CMD-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/commands/TFCCommands.java"
  - "src/main/java/net/dries007/tfc/common/commands/PlayerCommand.java"
  - "src/main/java/net/dries007/tfc/common/commands/CountBlockCommand.java"
  - "src/main/java/net/dries007/tfc/common/commands/ClearWorldCommand.java"
  - "src/main/java/net/dries007/tfc/common/commands/AddTrimCommand.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/command/**"
  - "core/src/test/java/dev/tfp/core/command/**"
  - "core/src/test/resources/dev/tfp/core/command/**"
  - "paper/src/main/java/dev/tfp/paper/command/**"
  - "paper/src/test/java/dev/tfp/paper/command/**"
ui: true
in_game_steps:
  - "tfp player Bot query thirst"
  - "tfp countblock 16 tfp:rock/raw/granite"
  - "tfp addtrim (holding armour)"
plan:
  - "Brigadier registration under /tfp (permission level 2) for the pure parsers in core/command; world access on the right threads (count over loaded chunk snapshots async; clear world via the commit queue); messages and lang keys."
  - "Done: /tfp player|count|clearworld|trim|weather (tfp.admin) built on core/command: paper/command DataCommandsModule + DataCommandTree (hooked into TfpCommands after `time`), PlayerCommands (entity thread, lazy thirst evaluated to now, vanilla food bar), WorldCommands + ChunkJob (per-chunk region tasks, 4 ms slices, loaded chunks only), TrimCommands (vanilla trim registries by item id), WeatherCommands (climate cycle flag), BlockKinds. Tests: paper command/{BlockKindsTest 2, WeatherCommandsTest 2, PlayerCommandsMockBukkitTest 8, WorldCommandsMockBukkitTest 7, TrimCommandsMockBukkitTest 6, DataCommandTreeMockBukkitTest 6}; BootstrapTest.commandTreeStructure and FeatureModulesTest cover the tree and permissions. Ledger row + 6 divergences (selector names only, loaded chunks only, no NBT predicate, vanilla trims only until PAR-DATA-11, weather flag, console position). Follow-ups: `/tfp propick` (TFC PropickCommand) and TFC trim materials (PAR-DATA-11) are not here; thunder/snow weather words and the weather in-game check are PAR-CLIM-07. Pending in-game check (container cannot run client)."
tests:
  - "paper: BootstrapTest tree"
  - "paper: MockBukkit execution per command"
done_criteria:
  - "Each command replies as TFC in-game"
---

# PAR-DATA-01: Commands under /tfp: player, count block, clear world, add trim, weather hooks

> see_not_parity: NP-CMD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
