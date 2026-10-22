---
id: PAR-TOOL-08
title: "Prospector's pick: scan, result message, `/tfp propick`"
area: TOOL
status: open
wave: 33
deps: [PAR-TOOL-06, PAR-DATA-01]
size: M
aliases: ["M4-PROS-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/PropickItem.java"
  - "src/main/java/net/dries007/tfc/common/items/ProspectResult.java"
  - "src/main/java/net/dries007/tfc/common/commands/PropickCommand.java"
  - "src/main/java/net/dries007/tfc/network/ProspectedPacket.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/prospect/**"
  - "core/src/test/java/dev/tfp/core/prospect/**"
  - "core/src/test/resources/dev/tfp/core/prospect/**"
  - "paper/src/main/java/dev/tfp/paper/prospect/**"
  - "paper/src/test/java/dev/tfp/paper/prospect/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/propick/copper"
  - "right-click a rock above a vein; screenshot actionbar"
plan:
  - "Use: snapshot the 25^3 cube on the region thread (chunk snapshots), decide async with ProspectRules, reply on the actionbar with TFC's translated message; cooldown 10 and 1 damage; `/tfp propick` command (radius, ore filter) as TFC."
tests:
  - "paper: MockBukkit use with a fixture snapshot"
  - "core: ProspectGoldenTest exists"
done_criteria:
  - "Propick over a known vein reports the TFC rating"
---

# PAR-TOOL-08: Prospector's pick: scan, result message, `/tfp propick`

> see_not_parity: NP-UI-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
