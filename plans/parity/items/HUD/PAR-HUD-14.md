---
id: PAR-HUD-14
title: "Server config for TFC's HUD display styles: health text style, climate temperature unit, XP bar options"
area: HUD
status: done
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
ui: false
in_game_steps: []
plan:
  - "From PAR-HUD-07 (docs/hud/versions.md section 2): our HUD uses TFC's defaults (healthDisplayStyle TFC, climateTooltipStyle CELSIUS, enableExperienceBar true, disabledExperienceBarStyle HOVER). Add server-wide config keys with TFC's names and defaults (NP-CLIENT-04): health-display-style (TFC, VANILLA, TFC_CURRENT, VANILLA_CURRENT, TFC's format strings) and climate-temperature-style (CELSIUS, FAHRENHEIT, RANKINE, KELVIN, TFC's conversions, one decimal)."
  - "Wiring needs shared files not owned here: the health text placeholder (integration/betterhud HudValues, PlayerNeedsHudService), the plate text (HudClimateValues, ClimateFormat callers), paper config.yml and the config loader. Claim those paths in this item's files_owned before starting, or split."
  - "Optional: enable-experience-bar false with BUMP/LEFT_HOTBAR: transparent experience_bar_* sprites (the level number is font text and stays), bar layouts moved down one XP row (HudLayouts), BetterHud replacements for the jump bar and TFC's fishing-pull bar. Decide with the owner; otherwise record as not built."
  - "Split (2026-11-05): HudValues, PlayerNeedsHudService and HudLayouts are owned by PAR-HUD-12 (and HudLayouts by PAR-HUD-10/15), ConfigCodec/TfpConfig/config.yml by PAR-INFRA-31, all open in wave 7, so this item builds the rules and the parsed config only; the wiring, the hud: section in config.yml and the in-game check moved to PAR-HUD-17 (wave 8). XP bar off (BUMP/LEFT_HOTBAR) not built: no owner decision, recorded in the ledger; HudStyles parses it and warns."
  - "Done: core HealthDisplayStyle (TFC's four String.format formatters, Locale.ROOT, format(HealthDisplay, HealthConfig)) and ClimateTemperatureStyle (TFC's float conversions, %.1f, TFC's en_us units, no -0.0, dash for NaN); paper hud/HudStyles (hud.* keys with TFC names and defaults, lenient warnings, toMap round trip, healthText/temperatureText). Tests: HealthDisplayStyleTest 11, ClimateTemperatureStyleTest 13 (goldens from TFC's expressions by a scratch program), HudStylesTest 7. In-game check deferred to PAR-HUD-17."
tests:
  - "core: golden strings for each style against TFC's String.format (e.g. 15/20 HP at modifier 1 -> TFC 750/1000, VANILLA 15.0/20.0; 20 C -> 68.0 F; R and K values from TFC's float arithmetic and %.1f, exported by a scratch script)"
done_criteria:
  - "Style rules match TFC's formatters (golden tests) and the hud.* keys parse with TFC's defaults"
  - "Deferred check tracked in PAR-HUD-17 (each style key changes the HUD text in game; defaults unchanged)"
---

# PAR-HUD-14: Server config for TFC's HUD display styles: health text style, climate temperature unit, XP bar options

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
