---
id: PAR-INFRA-35
title: "CE hit sound volume for template-built blocks (rock, soil, sand): the block sound table resolves tfp:settings/sound"
area: INFRA
status: done
wave: 7
deps: [PAR-INFRA-33]
size: S
aliases: ["follow-up of PAR-INFRA-33 (blocks whose sounds come only from a template)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/java/net/dries007/tfc/client/TFCSounds.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/integration/craftengine/CeBlockSoundTable*.java"
  - "paper/src/test/java/dev/tfp/paper/blocksound/CeBlockSoundTable*Test.java"
  - "docs/verification/PAR-INFRA-35/**"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11 and Paper 26.3 + client 26.3, TFC world, `-Dtfp.blocksound.log=true`; Bot mines a raw rock, a soil and a sand block; the server log shows each CraftEngine hit sound at TFC's volume/pitch"
plan:
  - "Current (PAR-INFRA-33): `CeBlockSoundTable` reads a block's hit/break sound from `settings.sounds` or `*_sound_<event>` template arguments. Rock, soil, sand and the other terrain blocks take their sounds from the `tfp:settings/sound` template (`minecraft:block.${sound_block_type}.<event>`, the argument set inside `tfp:settings/rock` and friends), so they are not in the table and CraftEngine's mining-loop hit stays at its fixed 0.5/0.5 (TFC: 0.25/0.5 for these (1.0, 1.0) SoundTypes)."
  - "Resolve the template chain for the table: read `templates:` sections too, follow `template:` references and `sound_block_type`/`sounds` through arguments and overrides, or have datagen write the resolved sound ids beside each terrain entry (then add the generator file to files_owned). Mind the table size (every rock x block type) and the load time on the I/O pool."
  - "Done: CeBlockSoundTable collects `templates` sections from all files and expands a block's settings (or block-level) template call (template id or list, arguments with the caller winning, overrides, merges, `${name}` / `${name:-default}`; ids still holding a placeholder are ignored); no datagen change. 1358 blocks in the table from the generated content, load ~0.2 s. Tests: CeBlockSoundTableTest (+2: generated terrain granite/dirt/sand/gravel/ore, template-chain semantics). In game on Paper 1.21.11 and 26.3 (TFC world): granite stone.hit, dirt gravel.hit, sand sand.hit 0.5/0.5 -> 0.25/0.5; evidence docs/verification/PAR-INFRA-35/."
tests:
  - "paper: CeBlockSoundTable resolves tfp:rock/raw/granite to minecraft:block.stone.hit 0.25/0.5 from the generated content"
done_criteria:
  - "Terrain blocks' CraftEngine hit sounds at TFC's volume/pitch in the log on 1.21.11 and 26.3; evidence in docs/verification/PAR-INFRA-35/"
---

# PAR-INFRA-35: CE hit sound volume for template-built blocks (rock, soil, sand): the block sound table resolves tfp:settings/sound

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
