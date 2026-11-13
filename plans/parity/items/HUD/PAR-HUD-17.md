---
id: PAR-HUD-17
title: "Wire the HUD display styles: hud.* keys in config.yml, health text and climate temperature placeholders follow HudStyles"
area: HUD
status: done
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
  - "Done: in-game check run 2026-11-13 on Paper 1.21.11 + 1.21.11 client at GUI scale 2 and 3: VANILLA/FAHRENHEIT, VANILLA_CURRENT/KELVIN and TFC_CURRENT/RANKINE change the health text and the plate after /tfp reload config, back to TFC/CELSIUS restores 850/850-style text and Celsius; pass (docs/verification/PAR-HUD-17/). Code and tests in 532b5b1 (ConfigCodecTest, ConfigServiceTest, HudValuesHealthTextTest, HudClimateValuesStyleTest, HudLayoutsTest)."
  - "UNBLOCKED (manager, 2026-11-07): the block was only a full disk. Code is complete; run the two in_game_steps, judge the screenshots, store docs/verification/PAR-HUD-17/ and set done."
  - "Blocked (2026-11-07) only on the in-game screenshot check (PROTOCOL section 6): the machine's root volume had about 55 MB free, so no headless Paper server and client could run (the first start failed with a full disk), and no test-running subagent was available. The code, tests, config.yml, catalogue, ledger and docs/hud/versions.md are complete in the PAR-HUD-17 commit; what remains is to run the two in_game_steps, judge the screenshots, store docs/verification/PAR-HUD-17/ and set done."
  - "Built (PAR-HUD-17 commit): core TfpConfig.Hud keeps the raw hud section (ConfigCodec: known top-level key, shape check, absent keys defaulted and listed); ConfigService types it with HudStyles.parse on every load (startup and /tfp reload config), adds its warnings to the outcome and publishes it to paper/hud/HudStylesHolder (one volatile immutable record); BetterHud text placeholder tfp_health_text (HudValues.TEXTS, HudStyles.healthText of the published HealthDisplay) and tfp_ambient_temp via HudStyles.temperatureText; HudLayouts health text pattern [tfp_health_text]; catalogue row par-hud-17-ph-health-text. Tests: ConfigCodecTest (+2), ConfigServiceTest (+2, reload swaps styles, bad value warns), HudValuesHealthTextTest (4), HudClimateValuesStyleTest (3), HudLayoutsTest (pattern, counts). /tfp debug chunk still prints ClimateFormat Celsius: follow-up PAR-HUD-18 (TfpPlugin is outside files_owned)."
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
