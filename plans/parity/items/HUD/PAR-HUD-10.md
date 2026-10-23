---
id: PAR-HUD-10
title: "Held temperature popup shows TFC's level text, verified in game"
area: HUD
status: open
wave: 6
deps: [PAR-HUD-08, PAR-HUD-09, PAR-HUD-11]
size: S
aliases: ["follow-up of PAR-HUD-08 (after PAR-HUD-09, which also owns HudLayouts.java)", "PAR-HUD-08 in-game reject 2026-10-23 (bare number, no colour, unreadable at GUI scale 2, stale value)"]
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
  - "screenshot centre popup at GUI scale 2 and 3 immediately after the command"
  - "tfp debug heat Bot 600, screenshot at once: popup reads Dark Red in DARK_RED, no stale Bright Red"
  - "tfp debug heat Bot 0, screenshot: popup hidden"
plan:
  - "PAR-HUD-08 publishes tfp_held_temp (number, condition of the popup) and the text placeholders tfp_held_heat (TFC COLOR style: en_us level name + U+066D stars, empty when cold) and tfp_held_heat_color (TFC Heat.getColor as #rrggbb: #aaaaaa GRAY, #aa0000 DARK_RED, #ff5555 RED, #ffaa00 GOLD, #ffff55 YELLOW, #ffffff WHITE; empty when cold). PAR-HUD-11 makes the values lazy at placeholder time. The generated popup layout still draws `[tfp_held_temp]°C` in near-black with a faint outline (unreadable at GUI scale 2)."
  - "Switch the popup layout text in HudLayouts to `[tfp_held_heat]` (keep the `tfp_held_temp > 0` condition) in the level's Heat colour: one text layer per colour group gated on tfp_held_heat_color (or on a level-index number placeholder), or a BetterHud colour override if it can read a placeholder. Use the normal MC text shadow (or a small backing plate) so the text is legible at GUI scale 2. Update the m3-surv-06-held-temp-popup catalogue row."
  - "Rerun datagen, then the in-game screenshot check (PROTOCOL §6) with the steps above; evidence in docs/verification/PAR-HUD-10/."
tests:
  - "HudLayoutsTest: popup text pattern names tfp_held_heat, one colour per TFC Heat colour group, condition unchanged, shadow on"
done_criteria:
  - "Holding a 900 C copper ingot shows `Bright Red٭٭٭٭` in RED (#ff5555) in the popup at GUI scale 2 and 3, legible at GUI scale 2; a cold ingot shows no popup"
  - "The popup changes level text and colour right after `tfp debug heat Bot <t>` with no visible lag"
---

# PAR-HUD-10: Held temperature popup shows TFC's level text, verified in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
