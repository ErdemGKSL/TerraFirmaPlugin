---
id: PAR-HUD-15
title: "HUD season shows TFC's 12 hemisphere-adjusted season names; info plate widened for them"
area: HUD
status: done
wave: 7
deps: [PAR-HUD-13]
size: S
aliases: ["PAR-HUD-13 review 2026-11-05: tfp_season shows the 4 Season enum names ('Fall'), TFC shows 'Early Autumn'"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/calendar/Month.java"
  - "src/main/java/net/dries007/tfc/util/calendar/ICalendar.java"
  - "src/main/java/net/dries007/tfc/client/screen/CalendarScreen.java"
  - "src/main/java/net/dries007/tfc/client/screen/button/PlayerInventoryTabButton.java"
  - "src/main/java/net/dries007/tfc/client/overworld/SolarCalculator.java"
  - "src/main/resources/assets/tfc/lang/en_us.json"
files_owned:
  - "core/src/main/java/dev/tfp/core/calendar/CalendarFormat.java"
  - "core/src/test/java/dev/tfp/core/calendar/CalendarFormat*"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudClockValues.java"
  - "integration/betterhud/src/test/java/dev/tfp/integration/betterhud/HudClockValues*"
  - "tools/datagen/src/main/java/dev/tfp/datagen/BetterHudPack.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/BetterHudPack*"
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudLayouts.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudLayouts*"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hud/m2_cal_02_info_plate.png"
  - "docs/art/hud/m2_cal_02_info_plate.png"
  - "art/raw/hud-info-plate/**"
ui: false
in_game_steps: []
plan:
  - "From the PAR-HUD-13 review: TFC shows the season as Month.getTranslationKey(Month.Style.SEASON), 12 names (tfc.enum.season.january..december = Winter, Late Winter, Early Spring, Spring, Late Spring, Early Summer, Summer, Late Summer, Early Autumn, Autumn, Late Autumn, Early Winter). CalendarScreen.java:106 uses getHemispheralCalendarMonthOfYear(inNorthernHemisphere) (the opposite month in the south); TFC's Season enum has no lang entry. Our tfp_season (HudClockValues -> CalendarFormat.season -> Month.season().name()) shows 'Fall' etc. for the northern month (ledger Divergences row 'TFC shows the season as the Month.Style.SEASON name')."
  - "Add TFC's 12 SEASON names to CalendarFormat (golden test against TFC's en_us.json) and make tfp_season per player: the hemisphere comes from the player's position (TFC SolarCalculator.getInNorthernHemisphere; core/climate/Solar has the port), read on the player's thread or from the per-player HUD snapshot, never from a Bukkit call in the placeholder."
  - "Widen the info plate to at least 189 px (text inset 5 + longest line 'September 30, 1000  (Early Autumn)' 179 + shadow 1 + margin 4; BetterHudPackTest.tfcSeasonNamesNeedTheWiderPlateOfParHud15). postprocess.py from the same raw, record the command in art/raw/hud-info-plate/notes.md; then turn the soft expectation into the hard plate check in dateLines() and update docs/hud/catalogue.md and the BetterHudPack javadoc."
  - "Then remove the ledger divergence row (or reduce it to what is left, e.g. the absolute-month tab tooltip if that is not built)."
  - "Done: core CalendarFormat.SEASON_NAMES / seasonName(ticks, daysInMonth, north) (TFC's 12 Month.Style.SEASON names, opposite month in the south; season() keeps the 4-value enum name for its callers); tfp_season (HudClockValues) shows the 12 names; HudClockValues.season(ClockSnapshot, north) ready for the per-player hemisphere, which needs BetterHudIntegration/HudSnapshot (owned by PAR-HUD-17) and moved to follow-up PAR-HUD-16 with the screenshot check; plate 189x22 from the same raw (notes.md), hard check in BetterHudPackTest.dateLines() over both hemispheres. Tests: CalendarFormatSeasonTest (3), HudClockValuesTest (3), BetterHudPackTest (dateLinesUseTfcSeasonNamesInBothHemispheres, plate check 179 + 10 = 189); BetterHudAdapterTest's two season expectations updated to the TFC names."
tests:
  - "core: CalendarFormat season names for all 12 months in both hemispheres equal TFC's en_us.json"
  - "tools/datagen: plate width >= inset + longest line with TFC's names (both hemispheres) + shadow + margin"
done_criteria:
  - "Deferred check tracked in PAR-HUD-16"
---

# PAR-HUD-15: HUD season shows TFC's 12 hemisphere-adjusted season names; info plate widened for them

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
