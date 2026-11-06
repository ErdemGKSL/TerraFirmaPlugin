---
id: PAR-HUD-16
title: "HUD season per player: tfp_season follows the viewer's hemisphere (southern players see the opposite month's name)"
area: HUD
status: open
wave: 9
deps: [PAR-HUD-17]
size: S
aliases: ["follow-up of PAR-HUD-15, done, holds its deferred in-game check, so it does not list it in deps (PROTOCOL §6.5) (per-player hemisphere needs BetterHudIntegration and HudSnapshot, owned by PAR-HUD-17)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/CalendarScreen.java"
  - "src/main/java/net/dries007/tfc/util/calendar/ICalendar.java"
  - "src/main/java/net/dries007/tfc/client/overworld/SolarCalculator.java"
  - "src/main/java/net/dries007/tfc/client/ClientHelpers.java"
files_owned:
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/BetterHudIntegration.java"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudClockValues.java"
  - "integration/betterhud/src/test/java/dev/tfp/integration/betterhud/HudClockValues*"
  - "integration/betterhud/src/test/java/dev/tfp/integration/betterhud/BetterHudAdapterTest.java"
  - "paper/src/main/java/dev/tfp/paper/integration/HudSnapshot.java"
  - "paper/src/test/java/dev/tfp/paper/integration/HudSnapshot*"
  - "paper/src/main/java/dev/tfp/paper/player/PlayerNeedsHudService.java"
  - "paper/src/main/java/dev/tfp/paper/hud/HudHemisphere*.java"
  - "paper/src/test/java/dev/tfp/paper/hud/HudHemisphere*"
ui: true
in_game_steps:
  - "tfp time add months until September; tp Bot to a northern-hemisphere z (Solar.inNorthernHemisphere true); screenshot the top-left plate at GUI scale 2 and 3 (1280x720): 'September 30, 1000  (Early Autumn)'"
  - "tp Bot to a southern-hemisphere z; wait 2 s; screenshot again at GUI scale 2 and 3: '(Early Spring)'"
plan:
  - "Current (PAR-HUD-15): core `CalendarFormat.seasonName(ticks, daysInMonth, north)` gives TFC's 12 Month.Style.SEASON names (golden against en_us.json, opposite month in the south); `HudClockValues.season(ClockSnapshot, boolean north)` exists; `HudClockValues.TEXTS` (tfp_season) gives the northern name for every player because `BetterHudIntegration` registers the clock texts as `player -> read.apply(clock.get())` with no player. The info plate is 189x22 and fits the longest line of either hemisphere."
  - "Publish the player's hemisphere from the player's thread (TFC `SolarCalculator.getInNorthernHemisphere(z, hemisphereScale)`, ported as core `Solar.inNorthernHemisphere`; the scale is the world's `OverworldClimate.hemisphereScale()`), e.g. a boolean in HudSnapshot written where PlayerNeedsHudService already publishes the position-dependent values (only when it changes)."
  - "Register tfp_season per player in BetterHudIntegration: `player -> HudClockValues.season(clock.get(), cache.snapshot(player.uuid()).northernHemisphere())`; never a Bukkit call on BetterHud's thread. Other dimensions: follow TFC's ClientHelpers.inNorthernHemisphere (ClimateRenderCache's hemisphere scale there)."
  - "Then remove the remaining part of the ledger Divergences row 'TFC shows the season as the Month.Style.SEASON name' (hemisphere)."
tests:
  - "integration/betterhud: tfp_season reads the published hemisphere per player (two uuids, one north one south, September -> Early Autumn / Early Spring); unset clock is '-'"
  - "paper: the hemisphere flag follows Solar.inNorthernHemisphere at the band edges for the world's scale"
done_criteria:
  - "Screenshots at GUI scale 2 and 3 show 'September 30, 1000  (Early Autumn)' fully on the plate in the north and 'Early Spring' in the south (moved here from PAR-HUD-15)"
---

# PAR-HUD-16: HUD season per player: tfp_season follows the viewer's hemisphere

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
