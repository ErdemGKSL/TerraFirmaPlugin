# S2: renderer-cube feel (destroy_stages, particles, sounds, mining) (PAR-INFRA-17)

**Status:** measured 2026-11-05 on Paper 1.21.11 (client 1.21.11) and Paper 26.3 (client 26.3), headless clients under
Xvfb + Mesa llvmpipe, OpenAL on the null device (`ALSOFT_DRIVERS=null`, so the sound engine and subtitles run).
CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0. Flat test world, stone test
row. **Verdict: renderer cubes are usable** (cracks, particles, sounds, light, outline, hitbox, leftovers, pick-block and
distance pass on both versions); mining time follows TFC's formula but is counted in client swing ticks (below); the
cube's `view_range` was raised from the planned 48 m to 256 m after the 48 m run left a hole. **Overlay ores: the
cracked overlay variant works** (route chosen; the dim fallback is not needed).

Evidence: `docs/verification/PAR-INFRA-17/` (verdict per criterion in `verdict.md`).

## 1. What was built

- **Templates (shipped, generated):** `tools/datagen` `dev.tfp.datagen.rendered` writes
  `configuration/templates/rendered_block.yml` (`tfp:block/rendered_cube`, `tfp:settings/destroy_stages`),
  `configuration/rendered_block_items.yml` (the ten crack items `tfp:render/destroy_stage/<n>`, CE model
  generation `minecraft:block/cube_all` over `minecraft:block/destroy_stage_<n>`, nothing copied) and the ten crack quads
  `tfp:item/render/overlay_crack_<n>` for the cracked overlay variants (`CrackedOverlays`, consumed by PAR-INFRA-15/16).
- **Spike block (not shipped):** `tfps2:metal/block/copper`, TFC's copper block as a renderer cube. Its config
  `spikes/s2-destroy-stages/pack/configuration/s2.yml` is generator output (`S2Spike`, checked by
  `RenderedCubeSpecTest`): TFC's `Metal.BlockType.blockProperties` (strength 5/6, `SoundType.METAL`,
  `requiresCorrectToolForDrops`) and the mineable tag (pickaxe). `build-pack.sh` copies it plus TFC's copper texture and
  the `normal_native_gold` overlay (from the checkout at run time) into a server's CraftEngine resources.
- **Spike plugin (not shipped):** `spikes/s2-destroy-stages` (`TfpSpikeS2`): `s2 place <ce id> x y z` (CraftEngine
  API), `s2 overlay x y z` (vanilla granite + one `item_display` quad per exposed face, 0.01 in front, the S1 geometry),
  `s2 mode crack|dim|none`; on `BlockBreakProgressUpdateEvent` the quads switch to `tfps2:overlay_crack_<n>` (composite
  of the overlay quad + our crack quad). Logs `[S2]` lines: dig start/abort, stage changes, break ticks with the tool,
  every block sound packet sent (PacketEvents), overlay removal when the host block is gone.

## 2. Method

```
export JAVA_HOME=/opt/jdk/25
./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc
./gradlew :dist:shadowJar
spikes/gradlew -p spikes/s2-destroy-stages jar
# per version: level-type=minecraft:flat in server.properties, then
spikes/s2-destroy-stages/build-pack.sh <run>
PACK_PORT=<pp> spikes/client/server.sh paper <mc> <run> <port> dist/build/libs/TerraFirmaPlugin-0.1.0-SNAPSHOT.jar \
  spikes/s2-destroy-stages/build/libs/s2-destroy-stages-spike.jar      # priming start, stop, delete world, start again
ALSOFT_DRIVERS=null MC_OPTIONS="guiScale:2 showSubtitles:true" spikes/client/session.sh <mc> <port> <gamedir> :<display>
```

Scene over RCON: `gamerule advance_time false`, `time set noon`, a stone floor, `s2 place tfps2:metal/block/copper` at
(2|3|4, -60, 3), `s2 overlay 6 -60 3`, Bot at (2.5, -60, 1.3) yaw 0 pitch 40 in survival with `tfp:metal/pickaxe/bronze`
(`ce item give`). Mining with `xdotool mousedown 1` / `mouseup 1`; screenshots with `import`; break times from the
`[S2] break ... ticks=` log (server ticks from the first `BlockDamageEvent`). `spikes/client/testroom.sh` was not used:
a flat stone row is enough and has no snow or tint to manage.

## 3. Findings per feel-checklist item (docs/research/ore-entity-renderer-design.md §6)

| # | Item | 1.21.11 | 26.3 |
|---|---|---|---|
| 1 | Cracks track progress. Renderer cube: CE `destroy_stages` | pass: stages 0..9 grow on every face as the server progress grows (frames in `1.21.11-cracks-renderer-cube.png`); alpha-blended crack texture, darker than vanilla's multiplicative crumbling but readable | pass (`26.3-cracks-particles-subtitles.png`) |
| 1 | Cracks. Overlay ore | pass with the cracked variant: the overlay quads swap to overlay + `destroy_stage_n` at each server stage (`[S2] stage n` lines one per 15 ticks by hand), cracks drawn over the specks, none hidden (`1.21.11-overlay-ore-cracked-variant.png`) | pass (same) |
| 2 | Break particles and sound | pass: barrier has `noTerrainParticles`, so the client spawns none; the `break` event's `minecraft:item` particles of the render item are copper-coloured (`1.21.11-break-particles-subtitles.png`); sound packets `block.metal.break` (CE replay, rewritten to `replaced.block.metal.break`), subtitles "Block breaking" / one "Block broken". Overlay ore: granite rock particles and `block.stone.break` (TFC parity: `tfc:block/ore` particle = `#all`, `SoundType.STONE`) | pass (same packets) |
| 3 | Hit, step, place sounds | pass: `block.metal.hit` while mining, `block.metal.step` walking over the cubes, `block.metal.place` on place; never the barrier's stone set | pass |
| 4 | Mining time | formula pass, timing partial: CE's `getDestroyProgress` is TFC's (hardness 5, bronze pickaxe speed 7.3, /30 correct, /100 hand: 21 ticks and 500 ticks). CE advances its progress only on server ticks in which the client swung its arm (`BukkitServerPlayer.tick` → `hasSwingHand()` → `tickBlockDestroy`), so a client below 20 ticks/s mines slower. Measured: tool 23-27 ticks (49-53 while the client was also taking screenshots), hand 582 ticks; vanilla iron block (same hardness, same tool) 18-19 ticks; vanilla stone by hand 151-152 (formula 150) | tool 35-38 ticks (vanilla iron block 17-20): the 26.3 client is slower under llvmpipe |
| 5 | No flicker or pop-in (overlay) | not measured here (PAR-INFRA-16, needs the runtime) | — |
| 6 | Light | pass: the barrier passes light, so the cube display is lit by its own cell; noon, midnight (dim, not black) and torch light correct (`1.21.11-light-noon-midnight-torch.png`). Residual: no ambient-occlusion contact shadow on the floor next to a cube (the barrier casts none) | pass |
| 7 | No z-fighting at 1-48 m | pass (cube faces against the floor and against each other; the crack shell is scaled 1.01) | pass |
| 8 | No seam | pass: adjacent cubes join without a gap (`1.21.11-seam-and-outline-x8.png`; the light edges are the texture's own bevel) | pass |
| 9 | Outline and hitbox | pass: the barrier's full-cube outline (8x crop); standing on top `Pos[1]` = floor + 1.0 (-59.0) | pass (-59.0) |
| 10 | Falling and collapse | not applicable to the cube (metal blocks do not fall); overlays: PAR-INFRA-16 | — |
| 11 | No leftover after `/fill`, TNT | pass: the cube displays go with the block (CE renderer), the spike overlays are removed within 2 ticks (`1.21.11-leftovers-fill-tnt.png`) | pass |
| 12 | Creative pick-block | pass: hotbar gets `craftengine:id` `tfps2:metal/block/copper` | pass |
| 13 | Distance | first run (`view_range` 0.75 on the cube): at 48 m every copper cube vanished and left a hole, because the barrier draws nothing (`1.21.11-distance-before-fix-view-range-0.75.png`). Fixed in the template: cube `view_range` 4.0 (256 m), visible for as long as its chunk; second run shows the cubes at 16-96 m with no ring (`1.21.11-distance-16-96m.png`). The overlay on native gold disappears between 48 and 64 m (the rock stays): visible only when looked for, the block is about 10 px tall there; judged acceptable | pass (same) |

## 4. Decisions for the families (PAR-DECO-09, PAR-DECO-12, PAR-MECH-01)

- Use `tfp:block/rendered_cube` via `RenderedCubeSpec` (carrier barrier, destroy stages, break-particle event, TFC sound
  set, hardness/resistance/tool; `correct_tools` beside the call for `requiresCorrectToolForDrops`).
- CE `settings` has no particle override; the break particles come from the `break` event (explicit action). There are
  no hit (dig) particles while mining a renderer cube: the barrier spawns none and CE has no per-tick hook in YAML.
- The cube keeps `view_range` 4.0; the crack stages keep 0.75 (48 m).
- Overlay ores (PAR-INFRA-15/16): emit the cracked variants (`CrackedOverlays.variants`, 10 per overlay, 530 for TFC's
  53 overlays) and swap them by the server's dig progress; the crack quad has the S1 overlay quad geometry.
