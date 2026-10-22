---
id: PAR-HUD-03
title: "`/tfp debug hunger <name> <0..20>` for exact low-food HUD states"
area: HUD
status: done
wave: 0
deps: []
size: S
aliases: ["gaps: UI defects (hunger debug)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/commands/PlayerCommand.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/boot/TfpCommands.java"
ui: false
in_game_steps: []
plan:
  - "Done in commit 6f8e8f2."
tests:
  - "BootstrapTest"
done_criteria:
  - "Committed"
---

# PAR-HUD-03: `/tfp debug hunger <name> <0..20>` for exact low-food HUD states

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
