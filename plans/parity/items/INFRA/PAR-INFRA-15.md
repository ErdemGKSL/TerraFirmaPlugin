---
id: PAR-INFRA-15
title: "Ore overlay datagen: ores take the host rock visual, overlay item models, always-on allocator rule, profiles level-0..2 only"
area: INFRA
status: open
wave: 6
deps: [PAR-INFRA-13, PAR-WORLD-17, PAR-INFRA-17]
size: M
aliases: ["owner decision 2026-10-22: ores as exposed-face overlays in every profile; level-3a/3b removed", "M1-DGN-04 (allocator)", "X-VIS-01"]
tfc_refs:
  - "src/main/resources/assets/tfc/blockstates/ore"
  - "src/main/resources/assets/tfc/models/block/ore.json"
  - "src/main/resources/assets/tfc/models/block/ore_column.json"
  - "src/main/resources/assets/tfc/models/block/ore"
  - "src/main/resources/assets/tfc/textures/block/ore"
  - "src/main/resources/assets/tfc/blockstates/rock/raw"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Ore.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/visuals/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/visuals/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/oreoverlay/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/oreoverlay/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/AssetPipeline.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/OreFlattener.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/VisualBudgetTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/VisualContentCheck.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/DatagenTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/PinnedCheckoutTest.java"
  - "content/generated/content/craftengine/tfp/configuration/templates/ore_overlay*.yml"
  - "core/src/main/java/dev/tfp/core/visuals/**"
  - "core/src/test/java/dev/tfp/core/visuals/**"
  - "core/src/main/java/dev/tfp/core/config/TfpConfig.java"
  - "core/src/main/java/dev/tfp/core/config/ConfigCodec.java"
  - "core/src/test/java/dev/tfp/core/config/ConfigCodecTest.java"
  - "core/src/test/java/dev/tfp/core/config/YamlDefaultsMergeTest.java"
  - "paper/src/main/resources/config.yml"
  - "paper/src/main/java/dev/tfp/paper/install/PackRebuildTrigger.java"
  - "paper/src/test/java/dev/tfp/paper/config/ConfigServiceTest.java"
  - "paper/src/test/java/dev/tfp/paper/install/InstallerTest.java"
  - "itest/src/test/java/dev/tfp/itest/cases/InstCasesTest.java"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11, then 26.3; fresh install (plugins/TerraFirmaPlugin and the CE pack removed), so the installer writes the level-2 allocation and rebuilds the pack"
  - "Check the startup log: `visuals 1.21/level-2/nether-end-off`, solid free = the full pool minus other packs, no warning; then set `visuals.profile.\"1.21\": level-3a` in config.yml, restart, and screenshot/grep the warning `level-3a was removed ... using level-2`"
  - "tfp time set noon; fill a 5x3x5 granite box with `tfp debug place` of tfp:rock/raw/granite and put tfp:ore/normal_native_gold/granite, tfp:ore/rich_hematite/granite, tfp:ore/diamond/gabbro (gabbro box) and tfp:ore/normal_malachite/shale (shale box) inside and on the box surface; screenshot: every ore looks exactly like its host rock (the overlay runtime is PAR-INFRA-16; until it is done the shipped mode stays FULL_CUBE, see plan) and the shale ore keeps the columnar end texture"
  - "Creative inventory: give Bot each of the four ore block items and screenshot the hotbar at GUI scale 2 and an 8x crop: the icon is rock + ore overlay (composite item model), grades distinguishable"
  - "Pick-block on the placed ores in creative: the hotbar gets the ore's own block item, not the rock"
plan:
  - "Current: AssetPipeline emits 1113 ore blocks (`ore/<ore>/<rock>`, `ore/<grade>_<ore>/<rock>`) with flattened one-PNG cube models (OreFlattener); Profile has LEVEL_0..LEVEL_3B, the gem rule (level-2) points gem ores at `rock/raw/<rock>#axis_y` and level-3a/3b merge grades; VisualAllocator allocates per profile x family x Nether/End; core TfpConfig/ConfigCodec/VisualSelection/ProfileCheck and paper config.yml know five levels, default level-3a on 1.21.x. Today 1374 visuals at level-0 of which 1113 are ores."
  - "Always-on ore rule (every profile): every ore appearance's look is its host rock's raw unit (`rock/raw/<rock>#axis_y`, including TFC's random mirrored/rotated raw variants; shale and claystone keep their cube_column look). Remove the gem-only rule and the grade-merging rules. Profile keeps LEVEL_0, LEVEL_1, LEVEL_2 only; `pendingRules()` lists only what is still missing (crate/workbench renderer, wood axis share, rock-agnostic deposits)."
  - "Delete level-3a and level-3b everywhere: the enum constants, their allocation folders under content/generated/content/visuals/<family>/level-3a|3b/** and reports/visual-allocation-level-3a|3b-*.md (regenerated tree; never by hand), their tests (VisualAllocatorTest, VisualBudgetTest, DatagenTest, PinnedCheckoutTest cases), InstCasesTest fixtures."
  - "Defaults: `Profile.defaultFor` and TfpConfig: level-2 on 1.21.x and 26.x; paper config.yml comments list level-0 | level-1 | level-2 and the `ore-overlay:` keys (cutoff-blocks 48, max-per-player 8000, surface-outcrops-full-range true; values from the S1 verdict of PAR-WORLD-17)."
  - "Migration: ConfigCodec reads `level-3a`/`level-3b` as LEVEL_2 and reports a warning naming the file and key (`level-3a was removed: ores are drawn as overlays and grades no longer cost visual states; using level-2`), at every start until the owner edits it; never rewrites the owner's value; unknown values keep today's warning with the new list."
  - "ProfileCheck: the 'next higher profile' suggestion stops at level-2; if level-2 does not fit, FAIL with the shortfall per pool, 'no higher profile exists' and the options (shrink or remove other CE packs, `use-nether-end-blocks` when the dimensions are off, `/ce clean-cache visual_block_states`); PackRebuildTrigger logs it unchanged otherwise. Stale-claim handling for removed level-3a/3b share ids keeps working (they are counted as ours and dropped)."
  - "Overlay item models (`dev.tfp.datagen.oreoverlay`): for each of the 53 TFC overlay textures one CE item `tfp:ore_overlay/<ore>` whose 1.21.4+ item definition is a single 16x16 quad (model generated by the model-authoring rules, not hand-written JSON), `shade: false`, `tintindex 0`, tint source `custom_model_data` colour 0 (default white); UV orientation per face matches the vanilla block face UVs of TFC's `tfc:block/ore` (golden). Cracked variants from PAR-INFRA-17 (overlay + `minecraft:block/destroy_stage_N`) if S2 chose them. Shared templates `tfp:item/ore_overlay` in templates/ore_overlay.yml."
  - "Block items: each ore block item gets a `minecraft:composite` item model (raw rock model + overlay cube) so the inventory icon looks like TFC's; no flattened PNG. Drop OreFlattener's per ore x rock PNGs from the pack (the class may stay for tests or be deleted); the pack shrinks by the 1113 textures (report the size delta)."
  - "Runtime data for PAR-INFRA-16: `content/generated/content/ore_overlay/ores.json` (CE ore block id -> overlay item id, host rock id) and `occluding.json` (every tfp block id whose visual does not occlude a full face: slabs, stairs, plants, devices, leaves pool, transparent renderers), generated from the same appearance data the allocator sees (a test cross-checks against the allocation's visual states)."
  - "Transition switch: until PAR-INFRA-16 is done the shipped ore look stays the flattened full cube (`OreLook.FULL_CUBE`, read from `tools/datagen/src/main/resources/dev/tfp/datagen/oreoverlay/mode.properties`, owned by PAR-INFRA-16), so no build ever shows ores as plain rock without overlays; the allocator, models and data above are emitted in both modes, and the HOST_ROCK allocations are what the budget tests check. PAR-INFRA-16 flips the switch as its last step."
  - "Optional (decide with a test, default off): solid pool before `absent_minor` while >= 1000 solid slots stay free (fewer suppressed vanilla blocks); record the decision in ARCHITECTURE.md 9.1."
  - "Docs: ARCHITECTURE.md 9.1 'until PAR-INFRA-15 is done' sentence removed; the allocation report states demand 261 visuals in every profile for the families emitted today."
tests:
  - "tools/datagen visuals: VisualAllocatorTest: with HOST_ROCK every ore appearance maps to its host raw rock unit in level-0, level-1 and level-2 (no ore unit exists); Profile.values() == [level-0, level-1, level-2]; defaultFor = level-2 for both families"
  - "tools/datagen oreoverlay: OverlayModelTest golden for one overlay item (quad, shade false, tintindex 0, tint source) and the 6 face UV orientations against tfc:block/ore; 53 overlay items, one per TFC overlay texture; composite block item model for a graded and a columnar (shale) ore"
  - "tools/datagen: VisualBudgetTest checks the defaults (level-2) per family with Nether/End on and off; DatagenTest/PinnedCheckoutTest updated (no level-3a/3b folders or reports)"
  - "core: ConfigCodecTest migration cases (level-3a, level-3b -> level-2 with the warning; level-4 unknown -> default with the list); ProfileCheckTest: level-2 not fitting fails with the shortfall and no suggestion; VisualSelectionTest without 3a/3b"
  - "paper: ConfigServiceTest/InstallerTest fixtures on level-2; itest InstCasesTest updated"
done_criteria:
  - "Shipped allocations exist for level-0, level-1, level-2 x {1.21, 26} x Nether/End on/off and no others; with HOST_ROCK the ore demand is 0 and the report shows 261 visuals (families emitted today) in every profile"
  - "Defaults level-2 on both families; a config naming level-3a/3b starts with level-2 and the warning; level-2 not fitting fails with the clear message (unit test)"
  - "Screenshots in docs/verification/PAR-INFRA-15/: host-rock look of buried ores (in HOST_ROCK test mode), ore block item icons at GUI scale 2, pick-block result"
  - "./gradlew check and verifyContent green"
---

# PAR-INFRA-15: Ore overlay datagen: ores take the host rock visual, overlay item models, always-on allocator rule, profiles level-0..2 only

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Wave note: added after the owner decisions of 2026-10-22 (ores as exposed-face overlays in every profile; level-3a/3b
removed). Wave 6 already holds four items; this is a fifth there on purpose (files disjoint from PAR-HUD-07, PAR-PROC-02,
PAR-SURV-02 and PAR-SURV-03). It must land before every ore/WORLD item that would otherwise assume full-cube ore visuals
(deps added to PAR-WORLD-08, PAR-WORLD-09, PAR-DECO-15). Design: `docs/research/ore-entity-renderer-design.md` §1-§2.

Note on shared files: `VisualBudgetTest.java` is also in PAR-DECO-15's `files_owned` (wave 49); this item only updates it
for the removed profiles and the new defaults.
