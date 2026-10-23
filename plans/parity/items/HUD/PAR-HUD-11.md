---
id: PAR-HUD-11
title: "Held item heat evaluated lazily at placeholder time (no popup lag after a heat change)"
area: HUD
status: open
wave: 5
deps: [PAR-HUD-08]
size: S
aliases: ["PAR-HUD-08 review 2026-10-23: value stale after `tfp debug heat` (693 shown after setting 900), lags while cooling"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java"
  - "src/main/java/net/dries007/tfc/config/ItemTemperatureDisplayStyle.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/integration/HudSnapshot.java"
  - "paper/src/test/java/dev/tfp/paper/integration/HudSnapshot*"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudValues.java"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudClimateValues.java"
  - "integration/betterhud/src/test/java/dev/tfp/integration/betterhud/HudClimateValues*"
  - "paper/src/main/java/dev/tfp/paper/player/PlayerNeedsHudService.java"
  - "paper/src/test/java/dev/tfp/paper/player/PlayerNeedsHud*"
  - "paper/src/main/java/dev/tfp/paper/TfpPlugin.java"
ui: false
in_game_steps: []
plan:
  - "TFC computes the held temperature when it renders. PAR-HUD-08 publishes a precomputed float once a second and on main-hand changes, so the popup lags up to a second while the item cools, and `/tfp debug heat` (TfpPlugin.debugSetHeat writes the PDC and sets the main hand, which fires no event) is not seen until the next reading."
  - "Publish the held stack's heat record into HudSnapshot instead of a float: stored temperature, stamp tick, heat capacity (0 = not heatable). The placeholders (tfp_held_temp in HudValues, tfp_held_heat and tfp_held_heat_color in HudClimateValues) evaluate it with the pure core rule (HeatState.temperatureAt, the same as ItemHeatHooks) against the calendar player tick, which any thread may read; never read the ItemStack on BetterHud's thread."
  - "Republish the record whenever the held stack's heat is written: debugSetHeat calls PlayerNeedsHudService.refreshHeld; other writers of the main hand (devices returning a stack) are covered by PAR-HUD-08's inventory listeners."
  - "Inject the ItemHeatHooks supplier at wiring time (TfpPlugin) and remove PlayerNeedsHudService.pluginHeat (the JavaPlugin.getPlugin lookup with its catch for unit tests)."
  - "Note: in TfpPlugin.java only the PlayerNeedsHudService construction and debugSetHeat change. HudValues is also owned by PAR-HUD-06; do not run both at once."
tests:
  - "integration/betterhud: the placeholder value tracks the calendar tick with no new publish (cooling between two reads)"
  - "paper: a heat set followed by an immediate read (next tick) shows the new value"
done_criteria:
  - "tfp_held_temp/tfp_held_heat follow the lazily computed temperature at evaluation time; after `tfp debug heat` the next placeholder read on the player's next tick shows the new value (unit tests)"
---

# PAR-HUD-11: Held item heat evaluated lazily at placeholder time (no popup lag after a heat change)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
