---
id: PAR-DATA-08
title: "Sounds: TFC sound events into the pack and played by features"
area: DATA
status: done
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
  - "Done: datagen `sound/TfcSounds` ships TFC's 56 non-entity (62 less peat and the firestarter) events and their oggs as `tfp:<event>` (animals, peat and firestarter stay in their packs), subtitles in `tfp_sound/lang`, the event list `data/tfp/data_maps/sounds.json`, the CC-BY-4.0 slide ogg in `third-party.json`/NOTICE; Paper `sound/Sounds` facade; bellows, quern, forging anvil hit, collapse, landslide, grill and powderkeg cool now play TFC's events (small edits outside `files_owned`, as instructed by the caller); ledger rows updated. Tests: `TfcSoundsTest` (4), `SoundsTest` (2); `checkNotice`. Pending in-game check (container cannot run client): bellows, quern and anvil sounds play. Follow-up: point the thatch/thin/charcoal CE block sound settings at the shipped tfp:block.* events."
tests:
  - "tools/datagen: every sounds.json entry resolves; checkNotice green"
done_criteria:
  - "Bellows/quern/anvil sounds play in-game"
---

# PAR-DATA-08: Sounds: TFC sound events into the pack and played by features

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

