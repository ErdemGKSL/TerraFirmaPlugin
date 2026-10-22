---
id: PAR-DATA-06
title: "Configuration parity: every TFC ServerConfig/CommonConfig key in config.yml"
area: DATA
status: open
wave: 37
deps: []
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
  - "src/main/java/net/dries007/tfc/config/CommonConfig.java"
  - "src/main/java/net/dries007/tfc/config/animals"
files_owned:
  - "paper/src/main/resources/config.yml"
  - "paper/src/main/java/dev/tfp/paper/config/**"
  - "core/src/main/java/dev/tfp/core/config/**"
  - "core/src/test/java/dev/tfp/core/config/**"
  - "docs/config.md"
ui: false
in_game_steps: []
plan:
  - "Generate a table of every TFC config key with default, our key and the consumer item; add missing keys with TFC defaults (consumers read them via ConfigService); keys of client-only options listed as divergences."
tests:
  - "core: every TFC key mapped or documented (parsed from ServerConfig.java at the pin)"
done_criteria:
  - "docs/config.md complete; reload applies"
---

# PAR-DATA-06: Configuration parity: every TFC ServerConfig/CommonConfig key in config.yml

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
