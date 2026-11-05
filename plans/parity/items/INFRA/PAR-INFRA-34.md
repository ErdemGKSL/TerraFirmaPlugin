---
id: PAR-INFRA-34
title: "Verify the ore overlay datagen in game: host-rock look of buried ores, composite ore block item icons, pick-block"
area: INFRA
status: open
wave: 8
deps: [PAR-INFRA-15, PAR-INFRA-16]
size: S
aliases: ["deferred in-game check of PAR-INFRA-15 (PROTOCOL 6.5)"]
tfc_refs:
  - "src/main/resources/assets/tfc/models/block/ore.json"
  - "src/main/resources/assets/tfc/models/block/ore_column.json"
  - "src/main/resources/assets/tfc/blockstates/rock/raw"
files_owned:
  - "docs/verification/PAR-INFRA-34/**"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11, then 26.3; fresh install (plugins/TerraFirmaPlugin and the CE pack removed), so the installer writes the level-2 allocation and rebuilds the pack; the shipped ore look is host_rock (PAR-INFRA-16 flipped tools/datagen/src/main/resources/dev/tfp/datagen/oreoverlay/mode.properties)"
  - "Check the startup log: `visuals 1.21/level-2/nether-end-off`, solid free = the full pool minus other packs, no warning; then set `visuals.profile.\"1.21\": level-3a` in config.yml, restart, and screenshot/grep the warning `level-3a was removed ... using level-2`"
  - "tfp time set noon; fill a 5x3x5 granite box with `tfp debug place` of tfp:rock/raw/granite and put tfp:ore/normal_native_gold/granite, tfp:ore/rich_hematite/granite, tfp:ore/diamond/gabbro (gabbro box) and tfp:ore/normal_malachite/shale (shale box) inside and on the box surface; screenshot: every buried ore looks exactly like its host rock, exposed faces carry the overlay, and the shale ore keeps the columnar end texture"
  - "Creative inventory: give Bot each of the four ore block items and screenshot the hotbar at GUI scale 2 and an 8x crop: the icon is rock + ore overlay (composite item model), grades distinguishable"
  - "Pick-block on the placed ores in creative: the hotbar gets the ore's own block item, not the rock"
plan:
  - "Current: PAR-INFRA-15 generates the host_rock allocations, the overlay quad items, the composite ore block items (template tfp:item/ore_block_item) and visuals/ore_overlay/*.json; the shipped look is full_cube until PAR-INFRA-16 flips mode.properties, so the host-rock look can only be checked after it. Moved here from PAR-INFRA-15 (PROTOCOL 6.5)."
  - "Run the in_game_steps with a cheap-model agent; the main session judges every screenshot."
tests:
  - "None beyond the in-game steps (the datagen and config tests live in PAR-INFRA-15)"
done_criteria:
  - "Screenshots in docs/verification/PAR-INFRA-34/: host-rock look of buried ores, overlays on exposed faces, ore block item icons at GUI scale 2 (8x crops), pick-block result, the level-3a migration warning; verdict.md per criterion"
---

# PAR-INFRA-34: Verify the ore overlay datagen in game: host-rock look of buried ores, composite ore block item icons, pick-block

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Deferred in-game check of PAR-INFRA-15 (PROTOCOL §6.5): the host-rock look needs the overlay runtime (PAR-INFRA-16, a
dependent of PAR-INFRA-15). PAR-INFRA-15 does not depend on this item.
