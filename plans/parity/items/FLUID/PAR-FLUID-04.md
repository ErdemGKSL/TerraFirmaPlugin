---
id: PAR-FLUID-04
title: "Hot spring healing and bubble columns"
area: FLUID
status: open
wave: 10
deps: [PAR-FLUID-03]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/HotWaterBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBubbleColumnBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldwater/hot/**"
  - "paper/src/test/java/dev/tfp/paper/worldwater/hot/**"
ui: true
in_game_steps:
  - "locate a hot spring; damage Bot 10; stand in it 10 s; screenshot health"
plan:
  - "Players standing in hot spring water heal hotWaterHealAmount periodically (entity scheduler, throttled); bubble columns give air when configured."
tests:
  - "paper: heal tick"
done_criteria:
  - "Health rises in a hot spring"
---

# PAR-FLUID-04: Hot spring healing and bubble columns

> see_not_parity: NP-FLUID-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
