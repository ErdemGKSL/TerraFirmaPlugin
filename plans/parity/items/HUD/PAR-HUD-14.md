---
id: PAR-HUD-14
title: "Server config for TFC's HUD display styles: health text style, climate temperature unit, XP bar options"
area: HUD
status: open
wave: 7
deps: [PAR-HUD-07]
size: S
aliases: ["PAR-HUD-07 follow-up: style options need code outside its files_owned", "NP-CLIENT-04"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/config/HealthDisplayStyle.java"
  - "src/main/java/net/dries007/tfc/config/ClimateTemperatureDisplayStyle.java"
  - "src/main/java/net/dries007/tfc/config/DisabledExperienceBarStyle.java"
  - "src/main/java/net/dries007/tfc/config/ClientConfig.java"
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/health/HealthDisplayStyle.java"
  - "core/src/test/java/dev/tfp/core/health/HealthDisplayStyle*"
  - "core/src/main/java/dev/tfp/core/climate/ClimateTemperatureStyle.java"
  - "core/src/test/java/dev/tfp/core/climate/ClimateTemperatureStyle*"
  - "paper/src/main/java/dev/tfp/paper/hud/HudStyles*.java"
  - "paper/src/test/java/dev/tfp/paper/hud/HudStyles*"
ui: true
in_game_steps:
  - "set health-display-style VANILLA and climate-temperature-style FAHRENHEIT in config.yml; tfp reload; screenshot the health text and the top-left plate at GUI scale 2"
plan:
  - "From PAR-HUD-07 (docs/hud/versions.md section 2): our HUD uses TFC's defaults (healthDisplayStyle TFC, climateTooltipStyle CELSIUS, enableExperienceBar true, disabledExperienceBarStyle HOVER). Add server-wide config keys with TFC's names and defaults (NP-CLIENT-04): health-display-style (TFC, VANILLA, TFC_CURRENT, VANILLA_CURRENT, TFC's format strings) and climate-temperature-style (CELSIUS, FAHRENHEIT, RANKINE, KELVIN, TFC's conversions, one decimal)."
  - "Wiring needs shared files not owned here: the health text placeholder (integration/betterhud HudValues, PlayerNeedsHudService), the plate text (HudClimateValues, ClimateFormat callers), paper config.yml and the config loader. Claim those paths in this item's files_owned before starting, or split."
  - "Optional: enable-experience-bar false with BUMP/LEFT_HOTBAR: transparent experience_bar_* sprites (the level number is font text and stays), bar layouts moved down one XP row (HudLayouts), BetterHud replacements for the jump bar and TFC's fishing-pull bar. Decide with the owner; otherwise record as not built."
tests:
  - "core: golden strings for each style against TFC's String.format (e.g. 15/20 HP at modifier 1 -> TFC 750/1000, VANILLA 15.0/20.0; 20 C -> 68.0 F; R and K values from TFC's float arithmetic and %.1f, exported by a scratch script)"
done_criteria:
  - "Each style key changes the HUD text in game; defaults unchanged"
---

# PAR-HUD-14: Server config for TFC's HUD display styles: health text style, climate temperature unit, XP bar options

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
