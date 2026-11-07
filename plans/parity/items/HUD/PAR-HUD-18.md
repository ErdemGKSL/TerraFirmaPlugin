---
id: PAR-HUD-18
title: "/tfp debug chunk climate lines follow hud.climate-temperature-style"
area: HUD
status: open
wave: 9
deps: [PAR-HUD-17]
size: S
aliases: ["follow-up of PAR-HUD-17 (the debug lines live in TfpPlugin, outside its files_owned)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/config/ClientConfig.java"
  - "src/main/java/net/dries007/tfc/config/ClimateTemperatureDisplayStyle.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/TfpPlugin.java"
ui: false
plan:
  - "Current (PAR-HUD-17): the HUD plate's tfp_ambient_temp follows hud.climate-temperature-style (HudStylesHolder.current().temperatureText), but TfpPlugin.climateLine and the 'HUD tfp_ambient_temp:' line of /tfp debug chunk still print ClimateFormat.temperature (Celsius, double half-up)."
  - "Use HudStylesHolder.current().temperatureText for the temperature and average temperature in climateLine and for the HUD line, so the debug output matches the HUD in every unit (TFC formats every climate temperature with climateTooltipStyle)."
  - "Update the ledger row of PAR-HUD-17 (debug chunk) and docs/hud/versions.md section 2."
tests:
  - "paper: the climate line and the HUD line of /tfp debug chunk in FAHRENHEIT (68.0°F at 20 C) and CELSIUS"
done_criteria:
  - "/tfp debug chunk prints the climate temperatures in the configured unit; defaults unchanged"
---

# PAR-HUD-18: /tfp debug chunk climate lines follow hud.climate-temperature-style

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
