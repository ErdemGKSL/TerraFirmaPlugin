---
id: PAR-HUD-10
title: "Held temperature popup shows TFC's level text, verified in game"
area: HUD
status: open
wave: 6
deps: [PAR-HUD-08, PAR-HUD-09]
size: S
aliases: ["follow-up of PAR-HUD-08 (after PAR-HUD-09, which also owns HudLayouts.java)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/config/ItemTemperatureDisplayStyle.java"
  - "src/main/java/net/dries007/tfc/common/component/heat/Heat.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudLayouts.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudLayoutsTest.java"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/ingot/copper"
  - "tfp debug heat Bot 900"
  - "screenshot centre popup at GUI scale 2 and 3"
  - "tfp debug heat Bot 0, screenshot: popup hidden"
plan:
  - "PAR-HUD-08 publishes the held temperature (tfp_held_temp, number, condition of the popup) and registers the text placeholder tfp_held_heat (TFC COLOR style: level name + U+066D stars, empty when cold). The generated popup layout still draws `[tfp_held_temp]°C`."
  - "Switch the popup layout text in HudLayouts to `[tfp_held_heat]` (keep the `tfp_held_temp > 0` condition), optionally coloured by level (Heat.getColor) via one layout per colour or a text colour placeholder; update the m3-surv-06-held-temp-popup catalogue row."
  - "Rerun datagen, then the in-game screenshot check (PROTOCOL §6) with the steps above."
tests:
  - "HudLayoutsTest: popup text pattern names tfp_held_heat, condition unchanged"
done_criteria:
  - "Holding a 900 C ingot shows `Bright Red٭٭٭٭` in the popup; a cold ingot shows no popup"
---

# PAR-HUD-10: Held temperature popup shows TFC's level text, verified in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
