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
ui: false
in_game_steps: []
plan:
  - "TFC (like vanilla) shows the health/food/thirst overlays only when the player's game mode has survival-style needs, and its food/thirst ticking is skipped for creative and spectator players."
  - "Publish the game mode (or a needs-active flag) in HudSnapshot and gate every TFC bar and popup in the BetterHud layouts on it (datagen HudLayouts conditions), hidden in creative and spectator, shown in survival and adventure."
  - "Pause the needs: thirst, hunger, nutrition and saturation do not drain and temperature does not hurt in creative/spectator; on a switch back the lazy state is re-stamped at the switch time so no time spent in creative is caught up. Handle PlayerGameModeChangeEvent on the player's entity scheduler and refresh the HUD at once."
  - "Follow PROTOCOL §4 rule 7 (game-mode and edge-case rule)."
  - "Done: HudSnapshot.needsActive published by PlayerNeedsHudService (GameModeNeeds.needsActive: survival/adventure); HudValues reads tfp_max_health and tfp_held_temp as 0 without needs and HudLayouts appends the gate tfp_max_health > 0 to every bars-HUD layout and popup (no new placeholder, so BetterHudAdapterTest's key list is unchanged); thirst clock re-stamped at each reading without needs and settled at the switch by onGameModeChange (MONITOR, next player tick, then republish). Hunger/saturation/nutrition stay vanilla-driven, which adds no exhaustion in creative/spectator; no temperature damage or heavy effects exist yet. The info plate stays in every mode (ledger divergence). Tests: GameModeNeedsTest 4, GameModeNeedsMockBukkitTest 4, HudSnapshotGameModeTest 3, HudValuesGameModeTest 2, HudLayoutsTest +2 (and the health/hunger/thirst bars now carry only the gate). In-game screenshots: the worker session could not run the headless server and client; moved to PAR-HUD-16 (does not list this item's dependents)."
tests:
  - "paper: game mode change to creative hides the bars (snapshot flag) and pauses needs; back to survival restores them with no catch-up"
  - "integration/betterhud + datagen: every TFC bar/popup layout carries the needs-active condition"
done_criteria:
  - "In creative and spectator no TFC bar or held-item popup shows and needs do not change; survival and adventure unchanged (unit tests)"
  - "Deferred check tracked in PAR-HUD-16"
---

# PAR-HUD-12: HUD and needs per game mode: bars hidden and needs paused in creative and spectator

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
