---
id: PAR-CLIM-08
title: "Lightning rules: enableLightning, log stripping, fire start"
area: CLIMATE
status: open
wave: 34
deps: [PAR-INFRA-01]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/lightning/**"
  - "core/src/test/java/dev/tfp/core/lightning/**"
  - "core/src/test/resources/dev/tfp/core/lightning/**"
  - "paper/src/main/java/dev/tfp/paper/lightning/**"
  - "paper/src/test/java/dev/tfp/paper/lightning/**"
ui: true
in_game_steps:
  - "summon minecraft:lightning_bolt ~ ~ ~5 (next to a TFC log)"
  - "tfp debug block on the log"
plan:
  - "On LightningStrikeEvent: cancel when enableLightning=false; with enableLightningStrippingLogs, 20% chance to strip logs around the strike as TFC (ForgeEventHandler lines ~932-950); vanilla fire stays."
tests:
  - "paper: MockBukkit strike with seeded random strips the expected logs"
done_criteria:
  - "Config keys present; in-game strike near a tree strips logs"
---

# PAR-CLIM-08: Lightning rules: enableLightning, log stripping, fire start

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
