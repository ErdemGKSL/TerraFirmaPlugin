# M0-VIS-01: Visual allocator prototype + in-game checks

**Status:** done for textures, mappings and pool reads on 1.21.11 and 26.3 (real vanilla clients). Client prediction, hitboxes and sounds are partly UNVERIFIED (see below). 2026-10-16.

## Setup
- `spikes/vis-01/build_pack.py <tfc> <out>` generates a CE pack (namespace `tfpspike`):
  - granite × {raw, cobble, mossy_cobble, smooth, bricks, cracked_bricks, mossy_bricks, chiseled, gravel}, plus hardened sharing raw's model;
  - hematite / native_copper / native_gold × {poor, normal, rich} on granite with **flattened** textures (TFC rock + overlay composited into one opaque PNG; first animation frame);
  - `small_native_copper` (TFC groundcover model) on `oak_button[face=floor,facing=north,powered=false]` plus a `powered=true` twin with the same model;
  - a `redstone_ore[lit=false]` + `[lit=true]` pair with one model;
  - `block_state_mappings` that hide every repurposed vanilla state (andesite→stone, copper_ore→stone, chiseled_stone_bricks→stone_bricks, double brick/mud-brick/stone-brick slabs → their full blocks, oak buttons → birch buttons, redstone_ore → stone).
- Visual sources on purpose: `auto_state: solid`, explicit absent-in-TFC states (andesite, copper_ore, chiseled_stone_bricks), explicit identity duplicates (double slabs), button, redstone_ore.
- `spikes/vis-01/Vis01Plugin` logs every `AutoStateGroup` after `CraftEngineReloadEvent` and places a grid (row 1 = our blocks, row 2 = the vanilla originals). `spikes/vis-01/run-client-check.sh <ver> <port>` runs Paper + the headless vanilla client (`spikes/client/`), builds the pack with `/ce reload all`, and takes screenshots.

## Results
**Runtime pool read works:** `AutoStateGroup.values()` → `candidateCount()` and `candidates().filter(isUsed)` after `CraftEngineReloadEvent`.

| Pool | 1.21.11 candidates | 26.3 candidates | used by CE defaults + spike |
|---|---|---|---|
| `solid` | 1289 | 1489 | 48 |
| `note_block` | 1100 | 1300 | 12 |
| `mushroom` | 189 | 189 | 36 |
| `leaves` / waterlogged | 117 / 117 | 117 / 117 | 1 / 1 |
| `tripwire` (lower/higher) | 126 (63/63) | 126 (63/63) | 7 |
| `chorus` | 0 | 0 | – |

These match the researched numbers exactly (1289 / 1489).

**Visuals (both client versions, screenshots `/tmp/claude-0/vis01-<ver>-{left,right,far}.png`):**
- All 9 granite variants and all 9 graded ores show TFC textures. Flattened ore textures render correctly on solid-layer visuals.
- Hardened (explicit `stone_brick_slab[type=double]`) and raw (`auto_state`) look identical. **One model on two vanilla states is accepted** (no model conflict), so "hardened = raw" works.
- The small ore piece on the button state shows the TFC groundcover shape; the `powered=true` twin looks identical.
- The redstone_ore pair shows the rich-gold texture.
- Every vanilla original renders as its mapping target: andesite → stone, double brick slab → bricks, double mud-brick slab → mud bricks, chiseled stone bricks → stone bricks. **`block_state_mappings` hide repurposed blocks** on both versions.
- CE logged **0 issues** for the pack on both versions.

**Gotchas found:**
- `auto_state: solid` allocates from `brown_mushroom_block` states first (enum order of the `SOLID` group), not note blocks. Harmless, but note blocks stay free longer.
- **CE extracts its default packs only if `plugins/CraftEngine/resources/` does not exist yet.** Writing our pack before CE's first start silently removes `internal` (mappings → all pools 0) and `default_templates`. Writing it in our `onLoad()` is fine, because CE has already extracted by then (seen in M0-CE-01 runs).
- CE's self-host must point at an address the client can reach (`resource-pack.self-host.ip`). For local tests, `127.0.0.1`.

## Decision
- The allocation approach is confirmed: explicit absent states, identity duplicates (double slabs), buttons and `auto_state` mix freely. Mappings hide the originals. Shared visuals via byte-identical models work.
- M1-DGN-04 reads pool capacity at runtime exactly like `Vis01Plugin`.
- The installer (M1-INST-01) writes into `plugins/CraftEngine/resources/tfp/` only from `onLoad()`, never before CE has initialised its folder.
- The CSV allowlist needs no changes from this test. No problem block was found among the ones exercised.

## Open / UNVERIFIED
- Client prediction:
  - button `powered` flash on click;
  - `redstone_ore` lit prediction when touched;
  - axe-strip prediction.
  These need an interactive click test (xdotool). Carry over to the M1 visual self-test (M1-INST-03) and the M2 visual checklist.
- Break sounds and hitboxes weren't checked (the headless client has no audio; hitbox outlines were seen for full cubes only).
- Render layers for cutout/translucent textures on 1.21.11 vs 26.3 weren't exercised (all spike textures are opaque).
