# S2: renderer-cube feel (destroy_stages, particles, sounds, mining) (PAR-INFRA-17)

**Status:** measured 2026-11-05 on Paper 1.21.11 (client 1.21.11) and Paper 26.3 (client 26.3), headless clients under
Xvfb + Mesa llvmpipe, OpenAL on the null device (`ALSOFT_DRIVERS=null`, so the sound engine and subtitles run).
CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449, PacketEvents 2.14.0. Flat test world, stone test
row. **Verdict: renderer cubes are usable** (cracks, particles, sounds, light, outline, hitbox, leftovers, pick-block and
distance pass on both versions); mining time follows TFC's formula but is counted in client swing ticks (below); the
cube's `view_range` was raised from the planned 48 m to 256 m after the 48 m run left a hole. **Overlay ores: the
cracked overlay variant works** (route chosen; the dim fallback is not needed).

Evidence: `docs/verification/PAR-INFRA-17/` (verdict per criterion in `verdict.md`).

**Review fix (same day):** a second pass on both versions re-shot light (torch in frame), TNT touching the row, 10-frame
correct-tool and overlay-ore crack captures, 8x crops of the copper seam, the ore/rock inner corner, the particle burst
and the 32/48/64 m row, and logged the volume and pitch of every block sound packet. It found the sound volume/pitch gap
(below, item 3) and fixed what the config can carry: `RenderedSound` now passes each event's volume/pitch.

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
  every block sound packet sent with its volume and pitch (PacketEvents), overlay removal when the host block is gone.

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

Re-run pitfalls: the copper cube exists only in the spike pack as `tfps2:metal/block/copper` (the dist jar ships no
renderer cube; there is no `tfp:metal/block/copper` yet). Run `build-pack.sh` before the server's first start: CraftEngine
does not rebuild a pack it has cached and hosted, so a pack added later stays missing (magenta/black checker, 0 `tfps2`
entries in the served zip) even after deleting its cache. `ce ...` and `tfp debug place` need a player sender (RCON and
`execute as` are refused), so give items and place blocks with `s2 place` / `s2 overlay` or from the client's chat with
op. The client needs `ALSOFT_DRIVERS=null`, otherwise OpenAL fails ("Failed to open OpenAL device") and no sound or
subtitle is produced. After a stop, wait about 45 s before restarting on the same ports.

## 3. Findings per feel-checklist item (docs/research/ore-entity-renderer-design.md §6)

| # | Item | 1.21.11 | 26.3 |
|---|---|---|---|
| 1 | Cracks track progress. Renderer cube: CE `destroy_stages` | pass: stages 0..9 grow on every face as the server progress grows (frames in `1.21.11-cracks-renderer-cube.png`; 100 ms frames through the break: `*-cracks-correct-tool-frames.png`); alpha-blended crack texture, darker than vanilla's multiplicative crumbling but readable | pass (`26.3-cracks-particles-subtitles.png`) |
| 1 | Cracks. Overlay ore | pass with the cracked variant: the overlay quads swap to overlay + `destroy_stage_n` at each server stage (`[S2] stage n` lines one per 15 ticks by hand), cracks drawn over the specks, none hidden (`1.21.11-overlay-ore-cracked-variant.png`; frames 0.75 s apart: `1.21.11-overlay-ore-crack-frames.png`) | pass (same; `26.3-overlay-ore-crack-frames.png`) |
| 2 | Break particles and sound | pass: barrier has `noTerrainParticles`, so the client spawns none; the `break` event's `minecraft:item` particles of the render item are copper-coloured (`1.21.11-break-particles-subtitles.png`); sound packets `block.metal.break` (CE replay, rewritten to `replaced.block.metal.break`), subtitles "Block breaking" / one "Block broken". Overlay ore: granite rock particles and `block.stone.break` (TFC parity: `tfc:block/ore` particle = `#all`, `SoundType.STONE`) | pass (same packets) |
| 3 | Hit, step, place sounds | ids pass: `block.metal.hit` while mining, `block.metal.step` walking over the cubes, `block.metal.place` on place; never the barrier's stone set. Volume/pitch: the first build passed only ids, so CraftEngine used its BlockSounds defaults (a 1.0/1.0 SoundType: every metal sound a third too low, hit twice as loud). Fixed: `RenderedSound` emits {id, volume, pitch} per event from the SoundType (metal 1.0/1.5) with the vanilla client's multipliers; measured place 1.0/1.2, step 0.15/1.5, fall 0.5/1.125, breaker's break 1.0/1.2 (TFC's). Left: CraftEngine sends hit at a fixed 0.5/0.5 (TFC 0.25/0.75) and a second break packet `replaced.block.metal.break` at a fixed 1.0/0.8 to every viewer (its level-event translation); divergence, follow-up PAR-INFRA-33 | same |
| 4 | Mining time | formula pass, timing partial: CE's `getDestroyProgress` is TFC's (hardness 5, bronze pickaxe speed 7.3, /30 correct, /100 hand: 21 ticks and 500 ticks). CE advances its progress only on server ticks in which the client swung its arm (`BukkitServerPlayer.tick` → `hasSwingHand()` → `tickBlockDestroy`), so a client below 20 ticks/s mines slower. Measured: tool 23-27 ticks (49-53 while the client was also taking screenshots), hand 582 ticks; vanilla iron block (same hardness, same tool) 18-19 ticks; vanilla stone by hand 151-152 (formula 150). Review-fix run: 22 ticks with the tool. The spike overlay ore sits on vanilla granite (hardness 1.5, 155 ticks by hand), not TFC's ore (`Ore.java`: rock category hardness 6.5, resistance 10): not a parity figure, the real ores come with PAR-INFRA-15/16 | tool 35-38 ticks (vanilla iron block 17-20): the 26.3 client is slower under llvmpipe; review-fix run 22 ticks |
| 5 | No flicker or pop-in (overlay) | not measured here (PAR-INFRA-16, needs the runtime) | — |
| 6 | Light | pass: the barrier passes light, so the cube display is lit by its own cell; noon, midnight (dim, not black) and torch light correct (`1.21.11-light-noon-midnight-torch.png`, torch in frame): cube top / floor mean luminance noon 124/124, midnight 37/38, torch 103/116. Residual: no ambient-occlusion contact shadow on the floor next to a cube (the barrier casts none); face shading is the display's entity lighting (top : north 1 : 0.77, vanilla 1 : 0.8) | pass: 126/125, 37/38, 102/114; a vanilla item entity beside the cubes is as dark, no full-bright faces |
| 7 | No z-fighting at 1-48 m | pass (cube faces against the floor and against each other; the crack shell is scaled 1.01); 1 m shot and 8x crops at 32, 48, 64 m (`*-zfight-1m-distance-x8.png`) | pass |
| 8 | No seam | pass: adjacent cubes join without a gap; 8x crop with no outline on the seam (`1.21.11-seam-corner-particles-x8.png`; the light and dark edges are the texture's own bevel). Ore/rock inner corner: no gap (same file) | pass (`26.3-seam-corner-particles-x8.png`) |
| 9 | Outline and hitbox | pass: the barrier's full-cube outline (8x crop); standing on top `Pos[1]` = floor + 1.0 (-59.0) | pass (-59.0) |
| 10 | Falling and collapse | not applicable to the cube (metal blocks do not fall); overlays: PAR-INFRA-16 | — |
| 11 | No leftover after `/fill`, TNT | pass: the cube displays go with the block (CE renderer), the spike overlays are removed within 2 ticks (`1.21.11-leftovers-fill-tnt.png`). TNT summoned touching the row destroys both cubes and the rock, nothing floats (`1.21.11-tnt-adjacent.png`): resistance 6.0 is TFC's, the carrier does not make the block blast-proof | pass (`26.3-tnt-adjacent.png`) |
| 12 | Creative pick-block | pass: hotbar gets `craftengine:id` `tfps2:metal/block/copper` | pass |
| 13 | Distance | first run (`view_range` 0.75 on the cube): at 48 m every copper cube vanished and left a hole, because the barrier draws nothing (`1.21.11-distance-before-fix-view-range-0.75.png`). Fixed in the template: cube `view_range` 4.0 (256 m), visible for as long as its chunk; second run shows the cubes at 16-96 m with no ring (`1.21.11-distance-16-96m.png`). The overlay on native gold disappears at 48 m (the rock stays): at 32 m the block is 15 px tall with specks, at 48 m 7 px tall with 1 px specks, so the pop changes single pixels; judged acceptable | pass (same) |

## 4. Decisions for the families (PAR-DECO-09, PAR-DECO-12, PAR-MECH-01)

- Use `tfp:block/rendered_cube` via `RenderedCubeSpec` (carrier barrier, destroy stages, break-particle event, TFC sound
  set, hardness/resistance/tool; `correct_tools` beside the call for `requiresCorrectToolForDrops`).
- CE `settings` has no particle override; the break particles come from the `break` event (explicit action). There are
  no hit (dig) particles while mining a renderer cube: the barrier spawns none and CE has no per-tick hook in YAML.
- The cube keeps `view_range` 4.0; the crack stages keep 0.75 (48 m).
- Sounds: `RenderedSound` carries the SoundType's volume/pitch per event; the hit and the level-event break sound
  need the packet rewrite of PAR-INFRA-33, which also moves the device blocks (`DeviceSound`) onto the same table.
- Overlay ores (PAR-INFRA-15/16): emit the cracked variants (`CrackedOverlays.variants`, 10 per overlay, 530 for TFC's
  53 overlays) and swap them by the server's dig progress; the crack quad has the S1 overlay quad geometry.
