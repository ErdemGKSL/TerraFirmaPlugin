---
id: PAR-DATA-08
title: "Sounds: TFC sound events into the pack and played by features"
area: DATA
status: open
wave: 37
deps: []
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/TFCSounds.java"
  - "src/main/resources/assets/tfc/sounds.json"
  - "src/main/resources/assets/tfc/sounds"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/sound/**"
  - "paper/src/main/java/dev/tfp/paper/sound/**"
  - "NOTICE"
ui: false
in_game_steps: []
plan:
  - "Copy TFC sounds (with their licenses: CC-BY/CC0 entries in NOTICE and third-party.json) into the CE pack under tfp:, a `Sounds` facade with TFC ids; block sound groups for CE blocks."
tests:
  - "tools/datagen: every sounds.json entry resolves; checkNotice green"
done_criteria:
  - "Bellows/quern/anvil sounds play in-game"
---

# PAR-DATA-08: Sounds: TFC sound events into the pack and played by features

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
