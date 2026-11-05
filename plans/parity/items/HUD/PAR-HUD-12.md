---
id: PAR-HUD-12
title: "HUD and needs per game mode: bars hidden and needs paused in creative and spectator"
area: HUD
status: done
wave: 7
deps: [PAR-HUD-11]
size: S
aliases: ["owner note 2026-10-23: in creative/spectator the health, thirst and hunger HUD must not show and the player must not get thirsty"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/common/component/food/FoodData.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/integration/HudSnapshot.java"
  - "paper/src/test/java/dev/tfp/paper/integration/HudSnapshot*"
  - "paper/src/main/java/dev/tfp/paper/player/PlayerNeedsHudService.java"
  - "paper/src/test/java/dev/tfp/paper/player/PlayerNeedsHud*"
  - "paper/src/main/java/dev/tfp/paper/player/GameModeNeeds*.java"
  - "paper/src/test/java/dev/tfp/paper/player/GameModeNeeds*"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudValues.java"
  - "integration/betterhud/src/test/java/dev/tfp/integration/betterhud/HudValues*"
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudLayouts.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudLayouts*"
ui: true
in_game_steps:
  - "gamemode survival Bot; screenshot: health, hunger, thirst bars visible"
  - "gamemode creative Bot; screenshot: no health/hunger/thirst/air/armour bars, no held-item heat popup"
  - "gamemode spectator Bot; screenshot: no TFC HUD"
  - "gamemode adventure Bot; screenshot: bars visible as in survival"
  - "tfp debug thirst Bot 50; gamemode creative Bot; wait 2 minutes of game time (time add 2400); gamemode survival Bot; read the thirst value: still 50 (no drain in creative, no catch-up)"
plan:
  - "TFC (like vanilla) shows the health/food/thirst overlays only when the player's game mode has survival-style needs, and its food/thirst ticking is skipped for creative and spectator players."
  - "Publish the game mode (or a needs-active flag) in HudSnapshot and gate every TFC bar and popup in the BetterHud layouts on it (datagen HudLayouts conditions), hidden in creative and spectator, shown in survival and adventure."
  - "Pause the needs: thirst, hunger, nutrition and saturation do not drain and temperature does not hurt in creative/spectator; on a switch back the lazy state is re-stamped at the switch time so no time spent in creative is caught up. Handle PlayerGameModeChangeEvent on the player's entity scheduler and refresh the HUD at once."
  - "Follow PROTOCOL §4 rule 7 (game-mode and edge-case rule)."
  - "Done: HudSnapshot.needsActive published by PlayerNeedsHudService (GameModeNeeds.needsActive: survival/adventure); HudValues reads tfp_max_health and tfp_held_temp as 0 without needs and HudLayouts appends the gate tfp_max_health > 0 to every bars-HUD layout and popup (no new placeholder). Thirst clock re-stamped at each reading without needs. onGameModeChange (MONITOR, on the player's thread inside the event, before Paper applies the mode) settles the clock at the switch and publishes with the flag of event.getNewGameMode() (publish(Player, boolean)), so the snapshot never lags the switch; a next-tick republish stays as a safety net, and onJoin republishes on the next tick so a join in creative/spectator does not show the default snapshot's bars. Hunger/saturation/nutrition stay vanilla-driven (no exhaustion in creative/spectator); no temperature damage or heavy effects exist yet. The info plate stays in every mode (ledger divergence). Review 2026-11-05: the first in-game run showed the HUD one mode behind at GUI 3 (the publish waited for the next tick); fixed by the in-event publish and re-captured at GUI 2 and 3 (docs/verification/PAR-HUD-12/)."
tests:
  - "paper: game mode change to creative hides the bars (snapshot flag) and pauses needs; back to survival restores them with no catch-up"
  - "integration/betterhud + datagen: every TFC bar/popup layout carries the needs-active condition"
done_criteria:
  - "In creative and spectator no TFC bar or held-item popup shows and needs do not change; survival and adventure unchanged (unit tests + in-game screenshots per mode)"
---

# PAR-HUD-12: HUD and needs per game mode: bars hidden and needs paused in creative and spectator

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
