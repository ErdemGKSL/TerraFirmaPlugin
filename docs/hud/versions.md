# HUD per MC version and TFC display styles

PAR-HUD-07 (old plan X-HUD-01). What the HUD depends on per supported client version, and how TFC's client-side HUD
display options map onto a server plugin.

## 1. Vanilla sprites hidden per version family

The bars replace vanilla's hearts, food, air bubbles and armour icons. The datagen ships a transparent 9x9 PNG over each
of those sprites in the CraftEngine pack (`HudLayouts.putHiddenVanillaSprites`,
`craftengine/tfp/resourcepack/assets/minecraft/textures/gui/sprites/hud/`). One pack serves every supported client, so
the datagen hides the union of all families' sprites; a sprite path one client lacks is harmless there.

| family | versions | list | extracted from | sprites |
|---|---|---|---|---|
| 1.21.x | 1.21.4 to 1.21.11 | `tools/datagen/src/main/resources/dev/tfp/datagen/hud/hidden-vanilla-sprites-1.21.txt` | 1.21.11 client jar | 59: 47 `heart/*`, 6 `food_*`, 3 `air*`, 3 `armor_*` |
| 26.x | 26.2, 26.3 | `tools/datagen/src/main/resources/dev/tfp/datagen/hud/hidden-vanilla-sprites-26.txt` | 26.3 client jar | 59, the same names |
| union (consumed by datagen) | all | `tools/datagen/src/main/resources/dev/tfp/datagen/hud/hidden-vanilla-sprites.txt` | the two lists above | 59 |

Findings (2026-11-05, client jars in `/opt/mcclient/versions/`):
- The `assets/minecraft/textures/gui/sprites/hud/` folders of 1.21.11 and 26.3 hold the same 87 files, and every file is
  byte-identical (`diff -r` of the extracted folders is empty). So the 26.x list equals the 1.21.x list.
- `atlases/gui.json` is the same in both (`gui/sprites` with no prefix, `mob_effect/` prefix), so the sprite ids the
  overrides replace are the same.
- 26.3's resource pack version is 97.1 (1.21.11: 75.0). The pack format and overlays belong to CraftEngine's pack build,
  not to this list.
- Only 26.3's client jar is on this machine; 26.2 is assumed to match (26.2 and 26.3 are one family in ARCHITECTURE.md).
  Recheck when a 26.2 jar is available.

How to re-extract (for a new MC version):
1. `unzip -Z1 <client.jar> | grep '^assets/minecraft/textures/gui/sprites/hud/'`, keep the names matching
   `heart/*.png`, `food_*.png`, `air*.png`, `armor_*.png`, strip the folder prefix, sort.
2. Same names as an existing family: add the version to that family's row above. Different names: add
   `hidden-vanilla-sprites-<family>.txt`, add the family to `HiddenVanillaSpritesTest.FAMILIES` and its client jar to the
   jar check, and make `hidden-vanilla-sprites.txt` the new union (the test fails until it is).
3. Rerun datagen and look at the hotbar in that client (vanilla hearts and food gone).

Tests: `tools/datagen` `HiddenVanillaSpritesTest` (each family list exists with 47/6/3/3 sprites; the consumed list is
the union; the generated CraftEngine pack holds every family's sprite; each family list equals its client jar's sprites
when `/opt/mcclient` has the jar, skipped elsewhere).

Not hidden, in either family: the hotbar, the crosshair, the experience bar and level, the horse jump bar, the locator
bar (`locator_bar_*`, the 1.21.6+ waypoint bar on the XP row). See section 2 for the XP bar.

## 2. TFC display styles

TFC keeps these as per-player client options (`TFCConfig.CLIENT`, `config/ClientConfig.java`). A vanilla client has no
client config, so the server owner picks one value for every player in `config.yml` section `hud` (NP-CLIENT-04;
PAR-HUD-14 built the styles, PAR-HUD-17 wired them). `ConfigService` parses the section on every load (startup and
`/tfp reload config`) and publishes it (`HudStylesHolder`); the placeholders read that value, never the file.

| TFC option (default) | TFC choices | ours now |
|---|---|---|
| `healthDisplayStyle` (`TFC`) | `TFC` `%.0f/%.0f` of health x 50; `VANILLA` `%.1f/%.1f`; `TFC_CURRENT` `%.0f` x 50; `VANILLA_CURRENT` `%.1f` | `hud.health-display-style`, all four: the health bar text is `[tfp_health_text]` = `HudStyles.healthText` of the published `HealthDisplay` (`850/850`, `17.0/17.0`, `850`, `17.0`); the bar fill still reads `tfp_health`/`tfp_max_health` |
| `climateTooltipStyle` (`CELSIUS`) | `CELSIUS`, `FAHRENHEIT` (x 9/5 + 32), `RANKINE` (x 9/5 + 491.67), `KELVIN` (+ 273.15), one decimal place | `hud.climate-temperature-style`, all four: the HUD plate's `tfp_ambient_temp` is `HudStyles.temperatureText` (TFC's float arithmetic, `68.0°F` at 20 C). `/tfp debug chunk` prints the climate temperatures and its `HUD tfp_ambient_temp` line in the same unit (PAR-HUD-18) |
| `enableExperienceBar` (`true`) | `true` keeps vanilla's XP bar; `false` hides it | `true`: the vanilla XP bar and level stay (their sprites are not overridden) |
| `disabledExperienceBarStyle` (`HOVER`) | used only while `enableExperienceBar` is `false`: `HOVER` elements stay in place; `BUMP` moves them down to the hotbar; `LEFT_HOTBAR` also draws the fishing-pull and jump bars as vertical bars left of the hotbar | no effect, as in TFC with the XP bar on |

So at TFC's defaults the HUD matches. The non-default XP styles (`BUMP`, `LEFT_HOTBAR`) need the XP bar hidden (transparent
`experience_bar_*` sprites, the level number is font text and cannot be hidden by a sprite) and a second set of bar layouts
moved down by the XP row; a horse jump bar and TFC's fishing-pull bar would then need BetterHud replacements. This is in
PAR-HUD-14 as optional and recorded in the ledger's Divergences.
