---
id: PAR-HUD-13
title: "Info plate fits the longest date line; BetterHud horizontal shift of the top-left info HUD"
area: HUD
status: done
wave: 6
deps: [PAR-HUD-06]
size: S
aliases: ["PAR-HUD-06 in-game finding 2026-11-05: date text overflows the 124 px plate in long months"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/BetterHudPack.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/BetterHudPack*"
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudLayouts.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudLayouts*"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hud/m2_cal_02_info_plate.png"
  - "docs/art/hud/m2_cal_02_info_plate.png"
  - "art/raw/hud-info-plate/**"
ui: true
in_game_steps:
  - "tfp time set noon in June, then tfp time add months until September and December (longest month names with Autumn/Winter)"
  - "screenshot the top-left plate at GUI scale 2 and 3 (1280x720)"
plan:
  - "Found in PAR-HUD-06 (docs/verification/PAR-HUD-06/): the date line '[tfp_date]  ([tfp_season])' is 134 GUI px for 'December 6, 1000  (Winter)' and up to 148 px for 'September 30, 1000  (Autumn)', but the plate is 124 px with the text starting about 5 px in, so long months run past the plate's right edge (gui2-december-topleft-4x.png)."
  - "Widening the plate alone is not enough: a 158x22 plate (postprocess.py from the same raw, --size 158x22) moved the plate about 17 GUI px right and the date text and thermometer about 17 px left in game (BetterHud re-centres the info HUD line by the image width). Fix the layout so the plate, text and thermometer keep their offsets for any plate width (check how BetterHudPack/HudLayouts place the info layouts and BetterHud's offset rules), then widen the plate (about 158 px: text start 5 + 148 + shadow 1 + margin 4) or shorten the line (e.g. season on the temperature line)."
  - "At GUI scale 3 on 1280x720 (GUI width 426.67) the whole info HUD sits 5/3 GUI px left of its layout x (the plate's left frame column is 1 screen px wide, cut by the screen edge); at GUI 2 it is 1 px left (the known SCREEN_SHIFT_X). Make the plate fully visible at both scales (e.g. layout x 2)."
  - "Update docs/hud/catalogue.md (plate size and position rows) and the BetterHudPack javadoc (124x22) with the new geometry."
  - "Done: 160x22 plate re-postprocessed from the same raw (text inset 5 + longest line 'September 30, 1000  (Summer)' 150 + shadow 1 + margin 4; the season is our tfp_season placeholder text 'Fall', one of the 4 Season enum names; TFC shows 12 hemisphere-adjusted names instead, 'Early Autumn' for September in the north, which is a divergence (ledger) fixed by follow-up PAR-HUD-15 with a plate of at least 189 px), HUD pixel.x +1 on the four info layouts (BetterHudPack.INFO_SHIFT_X). The 17 px re-centring of PAR-HUD-06 did not reproduce in game: BetterHud zero-sums each layout's width (LayoutComponentContainer), so no layout compensation is needed. Tests: BetterHudPackTest (6, incl. the soft TFC-name expectation for PAR-HUD-15). Verification pass at GUI 2 and 3: docs/verification/PAR-HUD-13/."
tests:
  - "tools/datagen: a test that the plate is at least as wide as the longest date line in the font's advance widths (all month names, two-digit day, four-digit year, every season) plus the text inset and margin"
done_criteria:
  - "Screenshots at GUI scale 2 and 3 show September and December dates fully on the plate, thermometer and temperature in place"
---

# PAR-HUD-13: Info plate fits the longest date line; BetterHud horizontal shift of the top-left info HUD

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
