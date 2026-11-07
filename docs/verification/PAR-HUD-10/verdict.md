# PAR-HUD-10: held temperature popup shows TFC's level text: verdict: pass

- Date: 2026-11-07. Tree `f2c8c3a` plus this item's change (`HudLayouts` popup: `[tfp_held_heat]` in six colour layers
  gated on `tfp_held_heat_color`, shadow `FF000000`, pixel y -70; `HudClimateValues` draws TFC's U+066D star as ASCII
  `*`), content regenerated with `:tools:datagen:generateContent`, then `:dist:shadowJar` in a separate Gradle run.
- Server: Paper 1.21.11 build 132 (`spikes/client/server.sh`, priming start, world deleted, second start; vanilla terrain,
  which a HUD check does not need), CraftEngine 26.9.2, BetterHud 2.2.0-SNAPSHOT-449. Client: vanilla 1.21.11 under Xvfb
  1280x720 (`spikes/client/session.sh`, `guiScale:2`, relaunched with `guiScale:3`); the pack applied 3 s after each join.
- Run and judged by the main session (no subagent tool was available to this worker). Server log: no error from our plugin
  (only the EULA flag notice and PacketEvents' update check, HTTP 403 from GitHub).

## Steps (RCON)

`gamemode survival Bot`, stone floor at y 99, `weather clear`, `time set noon`, `tp Bot 0 100 0 0 30`,
`ce item give Bot tfp:metal/ingot/copper`, then per GUI scale `tfp debug heat Bot 900`, `600`, `0`, each followed by 8
back-to-back screenshots (about 0.35 s apart). The text colour and extent were measured from the PNGs (most frequent
saturated colour in the band above the hotbar).

## Crops (2x unless named)

| file | content |
|---|---|
| `g2-900-bright-red.png`, `g3-900-bright-red.png` | 900 C: `Bright Red****` in `#ff5555` above the bars |
| `g2-900-text-6x.png` | the GUI 2 text at 6x: one-pixel glyph strokes with the black shadow |
| `g2-600-dark-red.png`, `g3-600-dark-red.png`, `g3-600-text-4x.png` | 600 C: `Dark Red` (no star: 600 is below 580 + 0.2 x 150) in `#aa0000` |
| `g2-0-hidden.png`, `g3-0-hidden.png` | 0 C: no popup |

## Checks

| criterion | GUI 2 | GUI 3 | evidence |
|---|---|---|---|
| 900 C copper ingot shows `Bright Red` and four stars in RED (`#ff5555`, measured exactly) | pass | pass | 900 crops; the stars are ASCII `*` (ledger divergence, the item's unblock decision), not TFC's U+066D |
| Legible at GUI scale 2 (black shadow on grey stone) | pass | pass | `g2-900-text-6x.png` |
| 600 C reads `Dark Red` in DARK_RED (`#aa0000`), no stale `Bright Red` | pass | pass | 600 crops; from the first changed frame on only `#aa0000` is present |
| 0 C hides the popup | pass | pass | 0 crops |
| Changes right after the command with no visible lag | pass | pass | the first frame (about 0.2-0.3 s after RCON) still shows the previous state, every frame from about 0.55 s on the new one; the same delay as the vanilla HUD's own reaction in PAR-HUD-12 |
| The popup stays centred (the missing-glyph shift of the 2026-11-06 run is gone) | pass | pass | text centre 636.5 px at GUI 2 and 634 px at GUI 3 against the screen centre 640: about 2 GUI px left, the same for every text, no shift when the stars appear |
| Clear of the bars (popup at pixel y -70, vanilla's selected-item name sits at H-59..H-51) | pass | pass | all crops |

While cooling the stars drop one by one between frames (900 C loses its fourth star below 890 C within about 2 s), as the
lazy placeholder (PAR-HUD-11) evaluates at each read.
