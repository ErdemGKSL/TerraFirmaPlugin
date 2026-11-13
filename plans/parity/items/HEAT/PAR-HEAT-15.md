---
id: PAR-HEAT-15
title: "Item bar config key: item-bars.heat in config.yml and TfpConfig, read on /tfp reload"
area: HEAT
status: open
wave: 12
deps: [PAR-HEAT-14]
size: S
aliases: ["follow-up of PAR-HEAT-14: its files_owned did not cover the typed config"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/config/ClientConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/config/TfpConfig.java"
  - "core/src/main/java/dev/tfp/core/config/ConfigCodec.java"
  - "core/src/test/java/dev/tfp/core/config/ConfigCodecTest.java"
  - "paper/src/main/resources/config.yml"
  - "paper/src/main/java/dev/tfp/paper/itembar/ItemBarModule.java"
  - "paper/src/test/java/dev/tfp/paper/itembar/ItemBarConfig*"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-HEAT-14's ItemBarModule reads `item-bars.heat` (TFC displayItemHeatBars, default true) straight from config.yml at enable; the key is not in the shipped config.yml, ConfigCodec reports `item-bars` as an unknown key, and /tfp reload does not re-read it."
  - "Add an `item-bars:` section with `heat: true` (comment: TFC displayItemHeatBars, server-wide per NP-CLIENT-04) to config.yml, a typed `ItemBars(boolean heat)` record to TfpConfig with ConfigCodec parsing (bad value: default with a warning), and make ItemBarModule's heat bar read `context.config().current()` on every call instead of its own file read (so /tfp reload applies it)."
tests:
  - "core: ConfigCodecTest round-trips item-bars.heat, a bad value warns and defaults to true, no unknown-key warning"
  - "paper: the heat bar follows the config after a reload"
done_criteria:
  - "item-bars.heat: false hides the heat bar after /tfp reload without a restart; no unknown-key warning; tests green"
---

# PAR-HEAT-15: Item bar config key

> see_not_parity: NP-CLIENT-04 (plans/parity/NOT-PARITY.md): server-wide setting instead of TFC's per-client option.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
