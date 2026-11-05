---
id: PAR-HUD-16
title: "In-game check of the HUD and needs per game mode (PAR-HUD-12)"
area: HUD
status: open
wave: 8
deps: [PAR-HUD-12]
size: S
aliases: ["PAR-HUD-12 2026-11-05: implemented and unit-tested; the worker session could not start the headless server and client, so the screenshot check moved here"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
files_owned:
  - "docs/verification/PAR-HUD-12/**"
  - "docs/verification/PAR-HUD-16/**"
ui: true
in_game_steps:
  - "gamemode survival Bot; screenshot: health, hunger, thirst bars visible"
  - "gamemode creative Bot; screenshot: no health/hunger/thirst/air/armour bars, no held-item heat popup"
  - "gamemode spectator Bot; screenshot: no TFC bars or popup (the top-left date/temperature plate stays, ledger divergence)"
  - "gamemode adventure Bot; screenshot: bars visible as in survival"
  - "tfp debug thirst Bot 50; gamemode creative Bot; wait 2 minutes of game time (time add 2400); gamemode survival Bot; read the thirst value: still 50 (no drain in creative, no catch-up)"
plan:
  - "Run PAR-HUD-12's in-game steps on the headless Paper 1.21.11 server and client (PROTOCOL section 6), GUI scale 2 and 3, and judge them against PAR-HUD-12's criteria. The hide gate is the BetterHud condition tfp_max_health > 0 on every bars-HUD layout and popup (HudLayouts.NEEDS_ACTIVE)."
  - "A failure is fixed through PAR-HUD-12's files (HudSnapshot, PlayerNeedsHudService, GameModeNeeds, HudValues, HudLayouts) in a follow-up item or here if it owns them."
tests:
  - "none beyond the screenshots"
done_criteria:
  - "In creative and spectator no TFC bar or held-item popup shows; survival and adventure show the bars; the thirst value is unchanged across a creative stretch (screenshots and verdict in docs/verification/PAR-HUD-12/)"
---

# PAR-HUD-16: In-game check of the HUD and needs per game mode (PAR-HUD-12)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
