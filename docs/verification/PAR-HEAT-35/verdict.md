# PAR-HEAT-35 verification

- Date: 2026-11-18
- Code under test: the PAR-HEAT-35 working tree on 1798716 (Claim commit); the jar built from it (`:tools:datagen:generateContent`, `:dist:shadowJar`).
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`), CraftEngine 26.9.2 self-hosted pack, BetterModel, BetterHud, PacketEvents as in `docs/testing/headless.md`. Client: vanilla 1.21.11 headless (Mesa llvmpipe), bot `Bot`, GUI scale 2, then 3 after a relog. Run and judged by the main session (no subagent tool in this run).
- Site: stone room (floor y 99, firepits at y 100), x -1490..-1468, z 1115..1131.

## What changed in game

`tfp:firepit` now sits on ONE carrier, `daylight_detector[inverted=false,power=0]` drawn empty (`transparent: true`), hidden from
real detectors as `power=1`; the four looks (`lit_x`, `lit_z`, `unlit_x`, `unlit_z`) are one CraftEngine `entity_renderer` item
display each (`tfp:render/firepit_lit` / `_unlit`, TFC's models, `0,180,0` on x and `0,90,0` on z), as the grill's. Template
`tfp:block/device_rendered`; luminance 15 lit, 0 unlit per variant; loot wood ash.

## Steps and results

| step | result | evidence |
|---|---|---|
| debug place unlit x, unlit z, lit x, lit z on stone | four TFC looks, axis z turned 90 against x, no detector look; selection box 16x6x16 (the carrier) | `gui2-four-looks-unlit-x-unlit-z-lit-x-lit-z.png`, `gui2-close-unlit-x-unlit-z-lit-x.png`, `gui3-firepits.png` |
| light (dark room, night) | floor lit around the lit pits only. Server light (location_check predicate): 14 next to a lit pit (source 15), 12 / 10 / 6 falling off with distance, an unlit pit adds nothing. The F3 screen of 1.21.11 shows no light line by default (needs F3+F6), so client light is judged from the picture | `gui2-dark-room-light.png` |
| 4 stick bundles + firestarter (sneaking) on the unlit x pit | `lit=true, burnTicks=600, burnTemperature=900`; the display morphed to the lit model at once, no flicker | `lit-readout.png`, `gui2-lit-after-firestarter.png` |
| empty hand on the lit pit | the firepit screen opens (GUI 2 and 3) | `gui2-cooking-screen-from-lit-pit.png`, `gui3-cooking-screen-from-lit-pit.png` |
| break the unlit z pit (survival, pickaxe) | wood ash drops (`tfp:powder/wood_ash`), the display is gone, no leftover | `gui2-after-break-wood-ash.png` |
| relog (twice: GUI 2, then GUI 3) | every display back, none doubled (no z-fighting) | `gui2-after-relog.png` |
| chunk reload (tp 2600 blocks away, 8 s, back) | every display back, none missing or doubled | `gui2-after-chunk-reload.png` |
| walk out to 80 blocks in 15-block steps and back | displays present on return, no leftover | `gui2-back-from-80-blocks.png` |
| fuelled lit pit, `tfp time add days 4` (PAR-HEAT-33) | unlit look within a second while loaded, still unlit after a chunk reload | `gui2-before-4-day-skip.png`, `gui2-after-4-day-skip-and-reload.png` |
| 30 more firepits (34 in view, half lit) | 34 item displays (one per firepit per viewer by construction); server 3.5 ms MSPT avg, 20 TPS; client 11-14 FPS before and after (software rendering, llvmpipe); 22 tx / 30 rx packets per second at rest | `gui2-34-firepits-f3.png` |
| real `daylight_detector` at power 0 (the carrier, hidden), 10 and 14 (freed by this item) | all three look like daylight detectors | `gui2-real-detectors-power-0-10-14.png` |

Notes:
- `/tfp debug place ...[lit=true]` makes a lit state without a burning device record, so those pits stay lit through the
  calendar jump; the fuelled pit lit with a firestarter is the real case and burns out. Debug-only, not in scope.
- Pre-existing, not caused by this item (the PAR-HEAT-23 crops show the same green at the plate edge): the daylight detector
  carrier's occlusion shape covers the whole bottom face, so the client culls the top face of the block under the firepit,
  and the gaps between TFC's rocks show what lies below (grass, water, a shaft). Follow-up PAR-HEAT-36.

## Stop rule (owner)

No flicker, no missing display on chunk load or relog, no leftover after breaking or distance, cost one display entity per
firepit per viewer (CE Community has no culling: it is sent with the chunk, at chunk view distance). Not blocking: the
entity look is kept.

## Criteria

- One vanilla carrier state; three daylight_detector states free again: **pass** (`FirepitTest`, `GeneratedCarrierTest`, in game the real detectors at 10 and 14).
- Lit and unlit TFC models on both axes, light 15/0, survives relog, chunk reload, distance and a calendar skip, break removes it and drops wood ash, cooking screen opens, GUI 2 and 3: **pass**.

Full-size lossless WebP screenshots: session scratchpad `ui/PAR-HEAT-35/`.
