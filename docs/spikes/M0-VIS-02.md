# M0-VIS-02: Exposed-only gem overlay
**Status:** done on Paper 1.21.11 and 26.3 (day, night, caves screenshots OK; cost measured). Folia not run. Re-measure with TFC worldgen in M2-GEN-15. 2026-10-16.

## Setup
- Plugin `spikes/vis-02` (`Vis02Plugin`, throwaway). Pack `spikes/vis-02/build_pack.py` (namespace `tfpvis02`):
  - `rock/granite`: TFC granite raw texture, `auto_state: solid` (the host rock).
  - `ore/ruby_granite`: the same visual as the rock. The gem block is a plain rock cube. The gem is an overlay on exposed faces only.
  - Overlay item `tfpvis02:gem/ruby` = TFC `block/ore/ruby.png` (16x16 RGBA) as an item model (`parent: item/generated`, 1.21.4+ item definition in `items/gem/ruby.json`).
- Mechanics: a face is exposed when its neighbour is air. Each exposed face gets one `ItemDisplay` (`FIXED`, `setPersistent(false)`):
  - location = centre of the **air cell** (so the display samples the air cell's light);
  - translation = `-normal * (0.5 - offset)`, which moves the quad from the air cell's centre to the rock face, then `offset` out into the air cell;
  - left rotation = `rotationTo(+z, normal)`.
  - Default `offset` = 0.01. The brief says "translated back 0.01". Read as "0.01 back out of the rock, into the air cell". Moving inward would hide the overlay (checked below).
- Exposure updates: `BlockPlace` and `BlockBreak` refresh the six neighbours on the next tick. Only the scripted build was exercised in game. Placing or breaking neighbours in game was not tested.
- Commands: `/vis02 build | offset <v> | refresh | measure | status | clear`.
- Scene (origin x=0, z=0; rock layer y=149; gems at y=150 unless noted):
  - ground: gems at even x (0-14) and even z (0-6) on the rock layer: 32 gems x 5 exposed faces = 160;
  - front wall at z=10 (gems at y=152): 8 gems, 9 faces (the x=0 gem also has an air neighbour at x=-1);
  - a second wall at z=14, same layout: 9 faces;
  - an enclosed cave (x 21-30, y 150-151, z 3-5) with a torch at (26,150,4): 5 side-wall gems + 4 ceiling gems = 9 faces.
  - Total: **57 gems, 187 exposed faces** (`vis02 build` reported `gems=57 displays=187`).
- Servers: Paper 1.21.11 (`c5eb079`) and 26.3 (`7ec11b2`), port 26300 (RCON 26310), display :104, 1920x1080 window, GUI scale 2. CE 26.9.2, PacketEvents 2.14.0. CE log: no `Found N issue`, pack loaded and uploaded on both.
- Commands: `spikes/vis-02/run.sh prepare|client`, RCON for the scene. Client launch: `spikes/gui-01/launch_client.py` (a helper from the other spike, read-only use).

## Results
### Visual checks (screenshots in `/tmp/claude-0/vis02-shots/`)
| Shot | 1.21.11 | 26.3 |
|---|---|---|
| Day overview (ground + two walls) | OK (`1.21.11-day-overview.png`) | OK (`26.3-day-overview.png`) |
| Day wall close-up, offset 0.01 | OK (`1.21.11-day-wall-off0.01.png`) | not taken |
| Night overview | OK (`1.21.11-night-overview.png`) | OK (`26.3-night-overview.png`) |
| Night wall close-up | OK (`1.21.11-night-wall.png`) | not taken |
| Cave, day (torch lit, enclosed) | OK (`1.21.11-cave-day.png`) | OK (`26.3-cave-day.png`) |
| Cave, night (torch lit) | OK (`1.21.11-cave-night.png`) | OK (`26.3-cave-night.png`) |

- **Texture:** the overlay is TFC's ruby texture, and the rock is TFC granite. Only exposed faces show the gem; the rock faces between gems stay plain.
- **Lighting:** at night the overlay is lit like the surrounding rock. In the cave the torch lights the overlays and the rock alike. Qualitative only; no numeric light comparison.
- **Z-fighting and the offset:**
  - At offset 0.01 the wall overlays are crisp, with no speckling.
  - At offset 0 they also look clean. The reason: `item/generated` is a 1/16-thick extrusion, so its front face sits **1/32 block (0.031) in front of the placement plane**. Offset 0 is already 0.031 clear of the rock.
  - Offset -0.03 (front face +0.001 in front of the rock): still visible, no speckle, at about 5 blocks.
  - Offset -0.035 (front face inside the rock): overlay gone.
  - So the visible front is at `offset + 0.031`. A flat zero-thickness model would need the 0.01 margin; the extruded model does not.
  - Not tested at longer distances, where z-fighting is more likely.

### Packet and cost (measured by `vis02 measure`: PacketEvents counts of SPAWN_ENTITY and ENTITY_METADATA to all players, one viewer)
| Version | Exposed faces | Spawn packets | Metadata packets | Packets per face | Main-thread spawn time |
|---|---|---|---|---|---|
| 1.21.11 | 187 | 192 | 395 | 3.14 | 28 ms (about 150 us/face) |
| 26.3 | 187 | 192 | 385 | 3.09 | 66 ms (about 355 us/face) |

- Spawn packets are 192 for 187 displays. The 5 extra are probably unrelated entities (not checked).
- A full refresh of 187 faces on the main thread took 22 ms (offset 0) and 62 ms (offset -0.035) on 1.21.11. First spawn with cold chunks took 709 ms (1.21.11) and 933 ms (26.3), including CE placement.
- **Cost per exposed gem:** this layout has 187/57 = 3.3 exposed faces per gem. That is about 10 packets per gem per viewer when the gem comes into tracking range, and about 0.5-1.2 ms of main-thread time per gem for the spawn.
- Every viewer receives its own copy: cost scales with viewers x exposed faces.

## Decision / recommendation
- Use one `item_display` per exposed face, placed in the air cell, with the gem overlay only on exposed faces. The look is right on both versions (day, night, caves).
- Keep the offset at **0.01 (outward)**, and do not use an inward offset. Keep the item model's extrusion in mind: visible geometry starts 0.031 in front of the placement plane.
- Budget: about 3.3 entities and about 10 packets per exposed gem per viewer, plus about 0.5-1.2 ms of main-thread time per gem when created. Avoid spawning overlays for unseen chunks. Measure again with worldgen (M2-GEN-15).
- Displays are entities. They must be re-created on chunk load and join (not tested here), and removed on chunk unload or block change.

## Open / UNVERIFIED
- Re-creation after chunk unload, server restart and player join: not tested (overlays were created by the build command only).
- Exposure updates from real placing and breaking (listeners exist, but the scene was built by script only).
- Folia threading (all spawns ran on the main thread on Paper). Region-thread placement for Folia is unverified.
- Z-fighting at longer distances than about 5 blocks, and at offset values between 0 and -0.03, is unverified.
- Numeric lighting comparison (overlay vs adjacent rock brightness); the check was visual only.
- Cost with worldgen density, many viewers, and the 5 unexplained extra spawn packets.
