---
id: PAR-INFRA-13
title: "Hitbox carrier selector: every CE block gets the vanilla state whose collision and selection shape best match TFC's"
area: INFRA
status: open
wave: 3
deps: [PAR-INFRA-08]
size: M
aliases: ["owner: hitbox carrier rule (charcoal forge snow-layer carrier generalised)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/GrillBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PotBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BellowsBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlockType.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/LooseRockBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Rock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/RockSpikeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/BloomBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/IngotPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCLeavesBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/ThatchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/device/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/ForgeDevices.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Firepit.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Grill.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Pot.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/ForgeDevicesTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/FirepitTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/GrillTest.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/PotTest.java"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11, then repeat on 26.x (pink_petals/wildflowers/leaf_litter shapes are client-side and version-dependent)"
  - "tfp time set noon; build a flat stone test row (fill ~ ~-1 ~ ~30 ~-1 ~ stone) and place one block of every pinned shape class 2 blocks apart: tfp debug place tfp:charcoal_forge[heat_level=0], tfp:firepit, tfp:grill, tfp:pot, tfp:bellows[facing=north], each loose rock count (tfp:rock/loose/granite[count=1|2|3]), one small ore, one groundcover of each TFC shape (SMALL, MEDIUM, FLAT, PIXEL_HIGH), charcoal pile layers 1..8 (until `tfp debug place` exists: give Bot the CE item and let Bot place it)"
  - "Selection outline (no F3+B): tp Bot 2 blocks in front of each block, pitch 35 deg, so the crosshair targets it; screenshot each; 8x crop of the outline; the outline must be the carrier's shape listed in the item body (forge 14 px full width; loose rock 1/2/3 grow as the table says)"
  - "Collision: tp Bot onto the top of each block that has collision (forge, firepit, grill, pot, charcoal pile n>1, farmland), wait 20 ticks, data get entity Bot Pos[1]; the stand height minus the floor equals the carrier's collision height; walk Bot through each no-collision class (loose rocks, small ores, groundcover, placed items) and check it passes"
  - "Break: Bot breaks each block by hand/pickaxe in survival; the break-crack overlay covers the outline, the CE drop appears, no vanilla carrier block or vanilla drop (snow ball, campfire, button) appears"
  - "Client prediction: Bot holds a vanilla snow layer item and a block item and right-clicks each snow-carried block; no ghost layer or ghost block flashes (record 10 frames 100 ms apart)"
plan:
  - "Current: carriers are picked ad hoc per family. ForgeDevices.FORGE_VISUAL = snow[layers=7] (selection 14 px like TFC, collision 12 px vs TFC 14 px, real snow[7] hidden to snow[8]); Firepit/Grill/Pot use campfire[lit=true|false] (16x7x16 for all three; TFC firepit is a 2 px plate + 12x4 core to 6 px, grill core to 11 px, pot core to 14 px; lit campfire also spawns smoke client-side); the bellows uses a full-cube smoker; Rock/Mud/Metal/Wood family generators only list blocks so far, and PAR-WORLD-09 plans buttons for every loose rock (count 2 is 12x2x12 in TFC, a button is 6x2x4: IoU 0.17). PAR-INFRA-08 adds a free-text `hitbox carrier` field to DeviceBlockSpec; this item replaces that free choice by a computed one."
  - "TFC shape table as data: `hitbox/tfc-shapes.json` generated (not hand-written) by a datagen step that reads the shape constants of the pinned TFC sources (VoxelShape fields, `box(...)` literals, `Shapes.or`, `SHAPE_BY_LAYER`, `computeHorizontalShapes`, `getShape`/`getCollisionShape` overrides, `noCollission()` in TFCBlocks/Rock.java) into per-state AABB lists {selection, collision} keyed by block id + state; where a shape is computed in code (PitKiln, Bellows, LogPile, Quern, Aqueduct, Crucible) a small ported Java evaluator with the EUPL + Ported-from header produces the boxes. Every TFC block state used by any generator must have a row (test)."
  - "Vanilla candidate table as data: `hitbox/vanilla-shapes-<version>.json` for 1.21.4, 1.21.11, 26.1.2, 26.3, dumped from each server jar (scratch Gradle task outside the repo or an itest dump; per state: selection AABBs, collision AABBs, occludes, light emission, replaceable, has client block-entity renderer, client particles, waterlogged/fluid, climbable, version availability). Merge it with `docs/research/vanilla-fullcube-supply.csv` (free / identity-dup / absent / tfc_present) and the CE default mappings so each candidate state carries its availability and the hide target needed when real instances exist."
  - "HitboxCarrier selector (pure Java, `dev.tfp.datagen.hitbox`): for a TFC state, score every allowed candidate state by voxel IoU on a 1/16 grid (1/32 for 0.25/0.5 px TFC boxes): score = 0.6 * IoU(selection) + 0.4 * IoU(collision), where IoU of two empty collision shapes is 1 and of empty vs non-empty is 0; selection weighs more because the outline must hug the model (this keeps the forge on snow[7] (0.943) over snow[8]/mud (0.925)). Hard filters: banned states (ice, slime, honey, soul sand, cobweb, sweet berry, powder snow, scaffolding, ladders, vines, kelp, end rod, cherry/pale-oak leaves, anything with a client block-entity renderer: chest, ender chest, enchanting table, bed, skull, decorated pot, conduit, sign), lit states that emit client particles (campfire lit, candle lit), light-emitting states unless the TFC block emits the same light, waterlogged states unless the TFC state is waterlogged. Penalties: replaceable carriers (client predicts placing into them: snow[layers=1], leaf_litter), full-occlusion carriers under a non-full model (x-ray of neighbours), a hide mapping needed (tfc_present states). Ties: fewer states consumed, then id order (deterministic)."
  - "Budget and conflicts: the selector runs as one allocation pass over the whole demand (all families, per profile x version family) before VisualAllocator's full-cube pass, so it shares its supply bookkeeping: each candidate state has capacity 1 model (model-conflict rule: two different models on one vanilla state abort the block; equal byte-identical variant JSON may share). When a state is already taken, the demand with the higher in-world count (worldgen frequency x states) keeps it and the other gets its next-best candidate; if nothing scores >= 0.5 the block uses `hitbox-only` mode: the best-shape carrier with one shared empty model plus `entity_renderer` for the look (only for rare blocks: devices, piles, lamps). tfc_present carriers (snow layers, natural vanilla states) are allowed only when their real instances can be hidden one hop to a pure vanilla state (as snow[7] -> snow[8]) and the hidden-state count is reported. The report lists per version family: chosen carrier, both IoUs, mode, hide mapping, states used per vanilla block; overflow fails datagen (never degrade silently, ask the owner)."
  - "Version families: the 1.21.x allocation may only use states that exist on 1.21.4 (min supported); wildflowers and leaf_litter exist from 1.21.5 and pink_petals' shape grows with flower_amount only from 1.21.5 (constant 16x3x16 on 1.21.4), so the 1.21.x family scores pink_petals with the 1.21.4 shape and cannot use wildflowers/leaf_litter; the 26.x family can use all three. The outline is drawn by the client from its own shapes, so a 1.21.4 client may see a different outline than a 1.21.11 client of the same allocation; the selector scores against the worst supported client of the family."
  - "Ground pieces (owner request): for TFC blocks that stack by count in one cell (loose rocks count 1-3, and any later count-stacked groundcover) the selector also considers the count-growing vanilla blocks so the hitbox follows the amount: pink_petals / wildflowers (facing x flower_amount 1-4, 16 states each, no collision, 1.21.5+: n quadrants of 8x3x8 rotated by facing), leaf_litter (facing x segment_amount, 16 states, n quadrants of 8x1x8, no collision, replaceable, 1.21.5+), candles 1-4 (collision, 17 colours + plain x 4 x lit x waterlogged; usable lit=false waterlogged=false: 68), sea_pickle 1-4 (collision; waterlogged=false only: 4 states), turtle_egg 1-4 x hatch 0-2 (collision; 12 states, 2 shapes), snow layers 1-8 (tfc_present). TFC's ground pieces have no collision (Rock.LOOSE/MOSSY_LOOSE and small ores are noCollission), so candles, sea pickles and turtle eggs score 0 on collision and lose to the no-collision blocks; the facing property of petals/wildflowers/leaf litter gives 4 distinct models per amount (useful as 4 looks, or keep one model per amount and rotate the 4 facings for variety). Budget: 3 segmentable blocks x 16 states = 48 looks on 26.x vs TFC demand of 21 rocks x (plain, mossy) x 3 counts = 126 loose-rock looks + small ores: the selector must report the shortfall and fall back to buttons (168 looks) / pressure plates per count, not drop models. Expected result on 1.21.x: count 1 -> button (IoU 0.67), count 2 -> stone/wood pressure plate (0.42), count 3 -> button (0.33) or brown/red mushroom (0.67, 2 states only); on 26.x count 1..3 may take leaf_litter/pink_petals amounts where the IoU is higher."
  - "Use it everywhere: ForgeDevices, Firepit, Grill, Pot and PAR-INFRA-08's DeviceBlockSpec take their carrier from `HitboxCarrier.select(tfcState, profile, versionFamily)`; no generator outside `hitbox/` and `visuals/` may contain a literal vanilla block-state string (`\"minecraft:<block>[`); HideTargets stays the one allow-listed place for hide targets. Family items that come later (see `Dependents` below) call the selector instead of choosing carriers."
  - "Snow-layer carrier notes (for every snow-carried block): (1) client prediction: a player holding a vanilla snow layer item can right-click a snow[layers<8] carrier and the client predicts an extra layer until the server corrects it; snow[layers=1] is replaceable, so any held block predicts placement into it. Cancel the interaction server-side and resend the block (CE does for its own blocks; verify), and keep snow[1] out of the candidate set. (2) Melting is not applicable: a CE block is a real server block, the client never random-ticks, and vanilla snow melting runs only server-side on real snow. (3) Sounds: the snow SoundType is silenced client-side for every snow state used as a visual, so real vanilla snow also goes silent client-side; CE replays sounds server-side, so the forge/pile must carry its TFC sound (charcoal: SoundType.WOOD-like `charcoal` set) and real snow its vanilla sounds through a tfp sound template; check in game. (4) Snow[layers=8] has a full top face: it supports torches/snow on top client-side; a hidden real snow[7] renders as snow[8] (one-hop rule)."
  - "Ledger: one Divergences row per residual mismatch the report lists (collision or selection IoU < 1), at least: charcoal forge snow[layers=7] collides at 12 px vs TFC 14 px (CharcoalForgeBlock.getShape = CharcoalPileBlock.SHAPE_BY_LAYER[7] for both); bloom and ingot piles on snow layers collide 2 px lower (TFC uses getShape for collision, snow uses layers-1); firepit/grill/pot shapes; TFC leaves, thatch and unwoven wattle have no collision but the leaves-pool/solid carriers do (no full-outline no-collision vanilla state is allowed); placed items and loose rocks keep no collision."
  - "Dependents that must call the selector (deps added): PAR-CLIM-04, PAR-HEAT-06, PAR-HEAT-08, PAR-HEAT-09, PAR-HEAT-10, PAR-HEAT-11, PAR-HEAT-12, PAR-PROC-04, PAR-PROC-08, PAR-PROC-10, PAR-WORLD-09, PAR-WORLD-10, PAR-WORLD-12, PAR-POT-01, PAR-POT-03, PAR-POT-08, PAR-FARM-01, PAR-FARM-03, PAR-METAL-03, PAR-METAL-08, PAR-METAL-12, PAR-METAL-16, PAR-METAL-19, PAR-CLIM-09, PAR-PHYS-04, PAR-DECO-01, PAR-DECO-06, PAR-DECO-07, PAR-DECO-09, PAR-DECO-12. Not edited because in progress or done: PAR-METAL-07 (done, wave 0: its forge carrier is re-derived here, output must stay snow[7]) and PAR-METAL-10 (wave 3, verification only: rerun its forge screenshots if this item changes the forge output)."
tests:
  - "tools/datagen hitbox: HitboxCarrierTest pins the chosen carrier, both IoUs and the mode for one representative state per shape class and version family: full cube, charcoal forge (snow[layers=7], 1.0 / 0.857), charcoal pile layers 1..8 (snow[layers=n], exact), firepit, grill, pot, bellows, farmland (vanilla farmland, exact), rock spike base/middle/tip, loose rock count 1/2/3, small ore, groundcover SMALL/MEDIUM/FLAT/PIXEL_HIGH, placed item, leaves (residual: collision 0)"
  - "Ground pieces: for loose rocks the chosen carrier's selection height is non-decreasing with count where TFC's is (count 3 is 4 px tall vs 2 px), every chosen carrier has no collision, and on the 26.x family a segmentable candidate is used when its IoU is higher; on 1.21.x no wildflowers/leaf_litter state is ever chosen"
  - "Budget: the selector plus VisualAllocator never assign two different models to one vanilla state (all profiles x version families); overflow throws; a tfc_present carrier always has a hide mapping"
  - "Guard: HardcodedCarrierTest scans tools/datagen/src/main/java outside `hitbox/` and `visuals/` and fails on any literal `\"minecraft:<block>[` state string or `auto_state` choice made outside the selector"
  - "TfcShapesTest: tfc-shapes.json regenerated from /opt/tfc equals the committed file; every TFC block state referenced by a generator has a row; spot values: CharcoalPileBlock layers 3 = selection 6 px / collision 4 px, LooseRockBlock count 2 = (2,0,2,14,2,14) no collision"
  - "Existing ForgeDevicesTest/FirepitTest/GrillTest/PotTest and VisualBudgetTest updated to the selector's output and green"
done_criteria:
  - "Every CE block datagen emits takes its visual state from HitboxCarrier (guard test green); the allocation report lists carrier, IoUs and mode for every block and fits every profile x version family"
  - "Screenshots in docs/verification/PAR-INFRA-13/ show the outline of each pinned class on 1.21.11 and 26.x matching the table, stand heights match the carrier collision, no ghost prediction with a held snow layer"
  - "Ledger Divergences has a row for every residual mismatch, including the charcoal forge 12 px vs 14 px collision"
---

# PAR-INFRA-13: Hitbox carrier selector: every CE block gets the vanilla state whose collision and selection shape best match TFC's

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Id note: PAR-INFRA-12 is taken by a pending follow-up of PAR-INFRA-04 (worker worktree), so this item is 13. Wave note: wave 3
already holds four items; this item is a fifth there on purpose (its files are disjoint from PAR-HUD-04, PAR-INFRA-06,
PAR-INFRA-09 and PAR-METAL-10) so that it lands before every family generator.

## Reference: TFC shape classes and the best vanilla carrier

Pixels (1/16 block). sel = selection (outline) box, col = collision box, "none" = no collision. IoU values are first
estimates from the 1.21.x shapes; the selector recomputes them from the dumped jar data.

| TFC class (examples) | TFC sel / col | best carrier (sel IoU / col IoU) | today |
|---|---|---|---|
| Full cube (rock, ore, soil, planks, logs) | 16 / 16 | solid pool / absent full cubes (1 / 1) | same |
| Layer pile 2n px (charcoal pile, bloom, ingot pile, pit kiln straw) | 2n / charcoal 2n-2, bloom+ingot 2n | snow[layers=n] (1 / charcoal 1, others (n-1)/n) | none yet |
| Charcoal forge | 14 / 14 | snow[layers=7] (1 / 0.857); alt snow[8] or mud (0.875 / 1) | snow[7] |
| Firepit (2 px plate + 12x4 core) | 6 / 6 | daylight_detector 16x6 (0.71 / 0.71) | campfire 7 px (0.61 / 0.61, lit smoke) |
| Grill (plate + core to 11) | 11 / 11 | snow[layers=5] (0.62 / ~0.6) | campfire (0.52) |
| Pot (plate + core to 14) | 14 / 14 | snow[7] taken by forge -> snow[6] (0.58 / 0.53) or hitbox-only | campfire (0.44) |
| Farmland | 15 / 15 | vanilla farmland (1 / 1) | none yet |
| Loose rock count 1, small ore, SMALL groundcover | 6x2x6 / none | floor button 6x2x4 (0.67 / 1) | button |
| Loose rock count 2, FLAT/TWIG groundcover | 12x2x12 / none | pressure plate 14x1x14 (0.42 / 1); 26.x pink_petals/leaf_litter amount 4 (0.38 / 1) | button (0.17) |
| Loose rock count 3, MEDIUM groundcover | 6x4x6 / none | brown/red mushroom 6x6x6 (0.67 / 1, 2 states) or button (0.33 / 1) | button |
| PIXEL_HIGH groundcover, scraping, placed item, poured glass | 16x1 or 14x1 / none | pressure plate (0.77 / 1); carpet has 1 px collision (col 0) | none yet |
| Thin spikes (rock spike, icicle, krummholz) | 2..14 / 4..12 / 6..10 posts | pointed_dripstone base/frustum/tip, lightning_rod 4x16 (~1 / ~1) | dripstone (icicle) |
| Lamp | lantern shape | lantern / soul_lantern (1 / 1, 8 states: hitbox-only + renderer per metal) | none yet |
| Sea pickle, candle, cake, walls, fences, slabs, stairs, trapdoors, buttons, plates | vanilla class shapes | the same vanilla block (1 / 1), freed sets only | none yet |
| Leaves, thatch, unwoven wattle | 16 / none | leaves pool / solid (1 / 0): residual, ledger | leaves pool |

## Reference: vanilla candidates (1.21.x unless noted)

| vanilla state | selection | collision | states | caveats |
|---|---|---|---|---|
| snow[layers=1..8] | 16 x 2n | 16 x 2(n-1) (none at 1) | 8 | tfc_present (hide one hop); n=1 replaceable; held-snow layer prediction |
| carpet (17 + moss, pale moss) | 16 x 1 | 16 x 1 | 1 each | |
| pressure plates | 14 x 1 (pressed 0.5) | none | 2 (16 weighted) | |
| buttons (floor) | 6x2x4 (pressed 1) | none | 24 per type | pair powered=false/true |
| slab bottom/top/double | 8 / 8 / 16 | same | 6 per type | freed sets only |
| trapdoor | 3 px plate (bottom, top, open wall) | same | 64 per type | |
| stairs | slab + step | same | 80 per type | |
| wall / fence | post 8x16 / 4x16 + arms | 24 px tall | multipart | |
| flower pot (+ potted) | 6x6x6 | same | ~40 | |
| cauldron | 16 shell, hollow 12x12 from 4 | same | 4 kinds | |
| candle 1..4 | 2x6x2, 6x6x3, 5x6x5, 6x6x5 | same | 18 colours x 16 | only lit=false, waterlogged=false (68) |
| sea_pickle 1..4 | 4x6x4, 10x6x10, 12x6x12, 12x7x12 | same | 8 | only waterlogged=false (light, water) |
| turtle_egg 1..4 x hatch | 9x7x9, then 14x7x14 | same | 12 | |
| pink_petals | 1.21.4: 16x3x16; 1.21.5+: n quadrants 8x3x8 | none | 16 (facing x amount) | flower, replace-by-same-item prediction |
| wildflowers (1.21.5+) | n quadrants 8x3x8 | none | 16 | absent on 1.21.4 |
| leaf_litter (1.21.5+) | n quadrants 8x1x8 | none | 16 | replaceable; absent on 1.21.4 |
| sculk_sensor | 16 x 8 | same | 96 | light 1 |
| daylight_detector | 16 x 6 | same | 32 | |
| campfire / soul_campfire | 16 x 7 | same | 32 each | lit = client smoke |
| stonecutter | 16 x 9 | same | 4 | |
| farmland / dirt_path | 16 x 15 | same | 8 / 1 | |
| mud | 16 | 16 x 14 | 1 | full-cube budget, occludes |
| lightning_rod | 4 x 16 post | same | 24 | |
| pointed_dripstone | tip 6x11, frustum 8, middle 10, base 12 wide | same | 40 | used by the icicle placeholder |
| lantern / soul_lantern | 6x7 + 4x2 (hanging +1) | same | 4 each | |
| brown / red mushroom | 6x6x6 | none | 1 each | |
| lily_pad | 14 x 1.5 | same | 1 | |
| scaffolding, ladders, vines, cobweb, chest, ender chest, bed, skull, decorated pot, enchanting table | - | - | - | banned (climbing, slowing, client block-entity renderers, particles) |
