---
id: PAR-HUD-17
title: "Wire the HUD display styles: hud.* keys in config.yml, health text and climate temperature placeholders follow HudStyles"
area: HUD
status: open
wave: 8
deps: [PAR-HUD-14, PAR-HUD-10, PAR-HUD-12, PAR-HUD-15, PAR-INFRA-31]
size: S
aliases: ["follow-up of PAR-HUD-14 (the wiring needs HudValues, HudClimateValues, PlayerNeedsHudService, HudLayouts, ConfigCodec and config.yml, owned by wave-7 items in progress)", "NP-CLIENT-04"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/config/ClientConfig.java"
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
  - "src/main/java/net/dries007/tfc/client/screen/ClimateScreen.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/config/TfpConfig.java"
  - "core/src/main/java/dev/tfp/core/config/ConfigCodec.java"
  - "core/src/test/java/dev/tfp/core/config/ConfigCodecTest.java"
  - "paper/src/main/resources/config.yml"
  - "paper/src/main/java/dev/tfp/paper/config/ConfigService.java"
  - "paper/src/test/java/dev/tfp/paper/config/ConfigServiceTest.java"
  - "paper/src/main/java/dev/tfp/paper/integration/HudSnapshot.java"
  - "paper/src/main/java/dev/tfp/paper/player/PlayerNeedsHudService.java"
  - "paper/src/main/java/dev/tfp/paper/hud/HudStyles*.java"
  - "paper/src/test/java/dev/tfp/paper/hud/HudStyles*"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudValues.java"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudClimateValues.java"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/BetterHudIntegration.java"
  - "integration/betterhud/src/test/java/dev/tfp/integration/betterhud/HudValues*"
  - "integration/betterhud/src/test/java/dev/tfp/integration/betterhud/HudClimateValues*"
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudLayouts.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudLayouts*"
  - "docs/hud/versions.md"
ui: true
in_game_steps:
  - "set hud.health-display-style VANILLA and hud.climate-temperature-style FAHRENHEIT in config.yml; tfp reload; screenshot the health text and the top-left plate at GUI scale 2"
  - "set both back to the defaults (TFC, CELSIUS); tfp reload; screenshot again: 850/850-style health text and the Celsius plate unchanged from before"
plan:
  - "Current (PAR-HUD-14): core `HealthDisplayStyle` (TFC's four formatters, `format(HealthDisplay, HealthConfig)`) and `ClimateTemperatureStyle` (TFC's four units, float arithmetic, %.1f, Locale.ROOT, no -0.0) are golden-tested; `paper/hud/HudStyles` parses the `hud:` section (keys health-display-style, climate-temperature-style, enable-experience-bar, disabled-experience-bar-style; TFC defaults; lenient warnings; `toMap` round trip) and formats `healthText`/`temperatureText`. Nothing reads it yet."
  - "Config: add the `hud` section to ConfigCodec's known top-level keys and TfpConfig (hold a `HudStyles`-equivalent value or the raw map handed to `HudStyles.parse`; core may not import paper, so either keep the enums in core and move the parse into core, or parse the raw section in ConfigService), keep warnings lenient; config.yml gets the commented section with TFC's option texts. ConfigServiceTest: the shipped config.yml parses without warnings."
  - "Publish the current styles where the placeholders can read them (a volatile `HudStyles` swapped on `/tfp reload`, or in HudSnapshot); never read config on BetterHud's thread."
  - "Health text: new text placeholder `tfp_health_text` = `HudStyles.healthText(HealthDisplay, HealthConfig)` and the bar pattern in HudLayouts `[tfp_health]//[tfp_max_health]` -> `[tfp_health_text]` (keep tfp_health/tfp_max_health numbers for the bar listener). HudLayoutsTest: the pattern names tfp_health_text."
  - "Climate text: `tfp_ambient_temp` in HudClimateValues uses `HudStyles.temperatureText`. Note: CELSIUS now goes through TFC's float + %.1f (e.g. 37.05 shows 37.0 as in TFC, ClimateFormat's double half-up showed 37.1); update the HudClimateValuesTest expectations and record it. Other climate texts (`/tfp debug chunk`, the climate GUI page when it exists) should use the same style."
  - "XP bar off (enable-experience-bar false, BUMP/LEFT_HOTBAR) stays not built unless the owner asks (ledger divergence); HudStyles already warns."
  - "Update docs/hud/versions.md section 2 (the 'ours now' column) and the ledger rows of PAR-HUD-14."
tests:
  - "integration/betterhud: tfp_health_text per style (850/850, 17.0/17.0, 850, 17.0) and tfp_ambient_temp per unit (68.0°F at 20 C)"
  - "core/paper: ConfigCodec/ConfigService read the hud section without warnings for the shipped config.yml; bad values warn and keep the defaults; /tfp reload swaps the styles"
  - "tools/datagen: HudLayoutsTest health text pattern"
done_criteria:
  - "Each style key changes the HUD text in game after /tfp reload; defaults unchanged"
---

# PAR-HUD-17: Wire the HUD display styles: hud.* keys in config.yml, health text and climate temperature placeholders follow HudStyles

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
