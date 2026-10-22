---
id: PAR-DATA-01
title: "Commands under /tfp: player, count block, clear world, add trim, weather hooks"
area: DATA
status: open
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
tests:
  - "paper: BootstrapTest tree"
  - "paper: MockBukkit execution per command"
done_criteria:
  - "Each command replies as TFC in-game"
---

# PAR-DATA-01: Commands under /tfp: player, count block, clear world, add trim, weather hooks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
