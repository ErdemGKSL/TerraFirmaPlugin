---
id: PAR-INFRA-17
title: "Renderer-cube feel (spike S2): destroy_stages, particles, sounds and mining for display-drawn full blocks"
area: INFRA
status: open
wave: 5
deps: [PAR-INFRA-08, PAR-INFRA-13]
size: M
aliases: ["research: ce-block-visual-workarounds-2 S2 (destroy_stages)", "owner: renderer-drawn blocks must feel like vanilla blocks (2026-10-22)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/Metal.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Ore.java"
  - "src/main/resources/assets/tfc/models/block/ore.json"
  - "src/main/java/net/dries007/tfc/common/blocks/rotation/AxleBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/rendered/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/rendered/**"
  - "content/generated/content/craftengine/tfp/configuration/templates/rendered_block*.yml"
  - "spikes/s2-destroy-stages/**"
  - "docs/spikes/S2-destroy-stages.md"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11, then Paper 26.3 + client 26.3 (spikes/client); survival; tfp time set noon; spikes/client/testroom.sh builds the stone test site"
  - "Place the S2 test blocks 2 apart on a stone row: tfp debug place tfp:metal/block/copper (barrier carrier + item_display cube with destroy_stages), a second one adjacent to it, and one spike overlay ore (raw granite visual + the spike plugin's 6 face overlays for tfp:ore/normal_native_gold/granite); until `tfp debug place` exists, give Bot the CE items and let Bot place them"
  - "Cracks: Bot mines each block with the correct tool (give Bot tfp:metal/pickaxe/bronze) and with a hand; record 10 frames 100 ms apart during each break; 8x crops; cracks must advance with progress on the renderer cube (destroy_stages) and on the overlay ore no crack may be hidden under the overlay specks (cracked overlay variant or dimmed overlay)"
  - "Particles and sounds: same breaks with the client sound log (subtitles on, screenshot the subtitle list); record the break particle colour (8x crop of the particle burst); copper block: metal sound and copper-coloured particles; ore: stone sound and rock particles (TFC parity: tfc:block/ore particle = #all)"
  - "Hit/step/place: Bot walks over and places each block; subtitles show the TFC SoundType, not the carrier's (barrier/stone)"
  - "Mining time: `tfp debug breaktime Bot <pos>` or a stopwatch over the recorded frames for hand, correct tool, wrong tool; compare with TFC hardness (copper block 4.0-ish metal hardness, ore = raw rock of its category 6.5 base)"
  - "Light: screenshot each block at noon, at midnight (tfp time set midnight) and with a torch placed 2 blocks away; no black or full-bright faces"
  - "Z-fighting and seams: screenshots at 1, 16, 32, 48 m, and 8x crops of the edge between the two adjacent copper blocks and of an inner corner between the overlay ore and rock"
  - "Outline and hitbox: crosshair on each block (pitch 35 deg), 8x crop of the outline; stand on top, `data get entity Bot Pos[1]` equals floor + 1.0"
  - "Leftovers: `fill ~-3 ~ ~-3 ~3 ~2 ~3 air` over the row and screenshot; replace, then `summon tnt` next to the row and screenshot after the blast: no floating display or overlay left"
  - "Pick-block: Bot in creative presses pick-block on each block (record the hotbar); the CE item of the block itself"
  - "Distance: screenshots of the overlay ore at 16, 32, 48, 64 and 96 m (flat test row against a sky background) to judge the cutoff pop"
plan:
  - "Current: metal and mechanical blocks are planned as `entity_renderer` cubes (ARCHITECTURE 9.1 always-on) but no template exists; PAR-INFRA-08 adds DeviceBlockSpec and PAR-INFRA-13 the hitbox carrier selector (barrier for full renderer cubes); CE 26.9.2 has `settings.destroy_stages` (Community, verified in the jar: DestroyStageDisplayEntity, keys items, position, rotation, brightness, view_range, progress) but nothing uses it."
  - "Spike S2 (spikes/s2-destroy-stages, not shipped): one barrier-carrier block drawn by an item_display cube with `destroy_stages.items` = 10 item models over the vanilla textures `minecraft:block/destroy_stage_0..9` (taken from the client jar by reference, never copied), plus one overlay ore built by the spike plugin; answer: do the CE crack displays follow progress for every viewer, which particles a barrier carrier shows on break and whether CE `settings` particle overrides change them, light and z-fighting of adjacent renderer cubes, and how vanilla cracks interact with a 0.01 overlay (cracks hidden under specks?). Write docs/spikes/S2-destroy-stages.md with the verdict per checklist item."
  - "Datagen (`dev.tfp.datagen.rendered`): `RenderedCubeSpec` (block id, carrier from HitboxCarrier, display item model, TFC SoundType, hardness/tool, particle source) and the shared templates `tfp:block/rendered_cube` + `tfp:settings/destroy_stages` in templates/rendered_block.yml: destroy_stages items over the vanilla crack textures with the cube's translation/scale, the TFC sound set, break particles (CE particle override if S2 shows it works on the barrier carrier, else a `tfp:break_particles` event that spawns `block_crack`/item particles of the material on break, an explicit action), `view_range` matching the overlay cutoff rule (no square ring)."
  - "For overlay ores (consumed by PAR-INFRA-16): emit the cracked overlay variants as composite item models (overlay + `minecraft:block/destroy_stage_N`, 10 per overlay, 53 overlays) or record the S2 fallback (dim the overlay to tint 0x808080 while mining) if the stages cannot be kept in step."
  - "The family items that draw renderer cubes (PAR-DECO-09 metal blocks, PAR-DECO-12 crates, PAR-MECH-01 mechanical blocks) use this template (deps added there); this item ships no family itself, only the spike blocks and the templates."
  - "Feel checklist (docs/research/ore-entity-renderer-design.md 6) is the acceptance list; each item gets a verdict line in docs/verification/PAR-INFRA-17/verdict.md."
tests:
  - "tools/datagen rendered: RenderedCubeSpecTest golden YAML for one renderer cube (barrier carrier, destroy_stages 10 items in order 0..9, TFC sounds, particles), CeYamlCheck clean"
  - "tools/datagen rendered: every destroy_stages item model references `minecraft:block/destroy_stage_<n>` (n = 0..9, no copied PNG); cracked overlay variants exist for all 53 overlays when the variant route is chosen"
done_criteria:
  - "docs/spikes/S2-destroy-stages.md has a verdict per feel-checklist item on 1.21.11 and 26.3"
  - "Feel (renderer cube): cracks track mining progress (destroy_stages), break particles and sound are the material's own, hit/step/place sounds are TFC's, mining time with hand/correct/wrong tool matches TFC within one tick-rounding, correct light at noon/midnight/torch, no z-fighting at 1-48 m, no seam between adjacent cubes, outline and hitbox of a full block, no leftover display after /fill and TNT, creative pick-block gives the block's item, no square ring at distance; screenshots in docs/verification/PAR-INFRA-17/"
  - "Feel (overlay ore, spike layer): no crack hidden under the overlay (cracked variant or dimmed overlay chosen and shown in 10-frame captures), rock particles and stone sounds as TFC, cutoff pop at 48 m judged acceptable on native gold"
  - "The rendered_cube templates and the destroy_stages setting are generated and used by the spike block without CE load issues"
---

# PAR-INFRA-17: Renderer-cube feel (spike S2): destroy_stages, particles, sounds and mining for display-drawn full blocks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Wave note: added after the owner decisions of 2026-10-22 (ores as exposed-face overlays; every renderer-drawn block must
feel like a vanilla block). Wave 5 already holds four items; this is a fifth there on purpose (files disjoint from
PAR-HUD-06, PAR-PROC-01, PAR-PROC-19 and PAR-SURV-01). Design and feel checklist:
`docs/research/ore-entity-renderer-design.md` §5-§6.
