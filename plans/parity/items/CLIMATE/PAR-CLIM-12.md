---
id: PAR-CLIM-12
title: "Climate-driven weather cycle: rain and thunder from the climate model replace vanilla's weather cycle; vanilla precipitation ticking off; local rain per player"
area: CLIMATE
status: done
wave: 13
deps: [PAR-CLIM-02, PAR-CLIM-03]
size: M
aliases: ["parity audit 2026-11-08: ServerLevelMixin.advanceWeatherCycle / tickPrecipitation and LevelMixin.isRainingAt named by no item"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/mixin/ServerLevelMixin.java"
  - "src/main/java/net/dries007/tfc/mixin/LevelMixin.java"
  - "src/main/java/net/dries007/tfc/mixin/BiomeMixin.java"
  - "src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java"
  - "src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java"
  - "src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java"
  - "src/main/java/net/dries007/tfc/util/climate/ClimateModel.java"
  - "src/main/java/net/dries007/tfc/client/ClimateRenderCache.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/weather/cycle/**"
  - "core/src/test/java/dev/tfp/core/weather/cycle/**"
  - "core/src/test/resources/dev/tfp/core/weather/cycle/**"
  - "paper/src/main/java/dev/tfp/paper/weather/cycle/**"
  - "paper/src/test/java/dev/tfp/paper/weather/cycle/**"
ui: false
in_game_steps: []
plan:
  - "Current (audit 2026-11-08): `OverworldClimate.rainIntensity` (TFC getRain) and `PrecipitationType.decide` are golden-tested, and our own consumers (snow, HUD, drinking) read them, but the world's weather is still vanilla's random cycle: nothing cancels vanilla advanceWeatherCycle, the server rain/thunder level does not follow the climate, vanilla tickPrecipitation still places vanilla snow and ice, and vanilla consumers of Level.isRainingAt (fire extinguishing, cauldron filling, mob burning, farmland moisture, lightning) do not ask the climate."
  - "core/weather/cycle: pure port of WeatherHelpers.advanceWeatherCycle: rainLevel/thunderLevel step by +/-0.01 per tick toward `getRain(calendarTicks) >= 0` and `getThunder(calendarTicks)`, with TFC's constants; port getThunder; the WorldTracker weather-enabled flag (default true) gives -1 / false when off. Lazy form: the level at a calendar tick is a pure function of the last change tick (no per-tick state needed to be stored)."
  - "paper/weather/cycle: in a TFC overworld (our generator) set gamerule doWeatherCycle false and drive the world's storm/thunder state from the decision on the global region (Folia) once per second; vanilla tickPrecipitation snow/ice is suppressed (BlockFormEvent for snow layers and ice made by weather, never the PAR-CLIM-03 SnowService writes); a /weather command or other plugin change is overridden on the next sync as TFC does; worlds without the overworld model keep vanilla weather (supportsRain false, see PAR-CLIM-11)."
  - "Local rain substitute for TFC's client mixins (LevelMixin.getRainLevel per player position, BiomeMixin.getPrecipitationAt): per player, set Player.setPlayerWeather(DOWNFALL/CLEAR) from PrecipitationService's decision at the player's position (NONE -> CLEAR, RAIN/SNOW -> DOWNFALL), so players in dry regions see no rain while it rains elsewhere; record the step change (no per-frame interpolation) as a Divergences row."
  - "Vanilla isRainingAt consumers: where Paper exposes the decision point (fire tick/extinguish via BlockBurnEvent/BlockFadeEvent, cauldron fill via CauldronLevelChangeEvent, entity combust in daylight is unaffected), gate it with the per-position climate decision; record every consumer we cannot reach as a Divergences row."
  - "Expose `WeatherCycle.enabled(world)` / `setEnabled` for PAR-CLIM-07 (`/tfp weather`) and PAR-CLIM-13 (`/tfp time set rain|clear`); persist the flag in the world PDC."
  - "Ledger: feature row 'Climate weather cycle' and Divergences rows (step-wise local rain, unreachable vanilla consumers); update the 'isWeatherEnabled toggle not ported' row."
  - "Done: core/weather/cycle (WeatherCycle: advanceWeatherCycle stepping, getThunder, lazy levelsAt; CycleRandom) and paper/weather/cycle (ClimateWeatherCycle: rule held off, storm/thunder from the climate once per second, per-player DOWNFALL/CLEAR, weather flag in the world PDC as a ModuleServices service; VanillaWeatherGates: weather snow/ice and rain/snow cauldron fill cancelled, rain-extinguished fire and weather lightning decided by the climate; WeatherCycleModule). Per-player weather uses DOWNFALL/CLEAR, which Paper ramps 0.01 per tick like TFC's ClimateRenderCache, so there is no step change. Tests: WeatherCycleGoldenTest (5, scratch-harness export at c3b4d9536), ClimateWeatherCycleMockBukkitTest (10). In-game screenshots, the itest case and the weather flag in PrecipitationService moved to PAR-CLIM-20 (PROTOCOL 3 and 6.5: no headless-client session in this run, files outside files_owned). Ledger: feature row and 4 divergence rows, isWeatherEnabled row updated."
  - "Repair (2026-11-14, in-game REJECT: dry-site shot showed rain): read from Paper 1.21.11 and the 1.21.11 client: CLEAR sends game event 1 (mojmap START_RAINING), which the client handles as rain level 0 at once, and DOWNFALL sends event 2 (level 1); Paper then sends its per-player level (0.01 per tick from where it was) only while it moves. So a fresh or rejoined player at a dry site is clear, and the failing shot was a bot teleported from the wet site about 50 ticks earlier, still inside the up-to-100-tick fade (as TFC's ClimateRenderCache) plus up to 20 ticks of timer lag. Fix: re-decide one tick after PlayerJoinEvent, PlayerRespawnEvent, PlayerChangedWorldEvent and PlayerTeleportEvent (on the player's thread, after sendLevelInfo); join/respawn/world change re-send the decision even when unchanged (a new client level starts at 0, so a DOWNFALL player at Paper level 1 would otherwise see no rain); the weather flag is loaded from the PDC on the global thread only. Ledger local-rain row now records the 20-tick lag, the one-tick instant-level flash, the arrival fade and the re-send; isWeatherEnabled row notes TFC's client ignores the flag. Tests: ClimateWeatherCycleMockBukkitTest 13. PAR-CLIM-20 in_game_steps use two bots (or wait 120 ticks after a teleport) so both shots are the same tick."
tests:
  - "core: WeatherCycleGoldenTest (rain/thunder level sequence over calendar ticks vs a scratch-harness export of TFC's advanceWeatherCycle at c3b4d9536; getThunder golden; weather disabled gives -1/false)"
  - "paper: MockBukkit: doWeatherCycle off in a TFC world, storm state follows the decision, /weather override is undone, per-player weather follows PrecipitationService, vanilla weather kept in a non-TFC world"
  - "itest: `weather.cycle` case (storm state at two forced calendar ticks): moved to PAR-CLIM-20 (the case file is outside files_owned)"
done_criteria:
  - "The world's rain and thunder follow TFC's climate cycle; vanilla weather snow/ice never forms; a player at a dry site sees no rain while a player at a wet site does (per-player weather, MockBukkit, incl. join/respawn/world change/teleport); tests green"
  - "Deferred check tracked in PAR-CLIM-20"
---

# PAR-CLIM-12: Climate-driven weather cycle

> see_not_parity: NP-CLIM-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
