---
id: PAR-HUD-19
title: "Health and thirst bar states: hurt/regeneration glow, absorption in the fill and the yellow surplus bar, thirst overheat tint"
area: HUD
status: done
wave: 10
deps: [PAR-HUD-17, PAR-HUD-16, PAR-HUD-12, PAR-SURV-04]
size: M
aliases: ["parity audit 2026-11-08: ledger divergence rows 'TFC draws an extra overlay over the whole health bar', 'TFC adds absorption to the health bar' and 'TFC tints the thirst bar while heat drives thirst' all say revisit Yes and name no item"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
  - "src/main/java/net/dries007/tfc/common/player/PlayerInfo.java"
  - "src/main/resources/assets/tfc/textures/gui/icons/overlay.png"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudLayouts.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudLayouts*"
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudArt*.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudArt*"
  - "tools/images/hud_layers.py"
  - "docs/hud/catalogue.md"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudValues.java"
  - "integration/betterhud/src/test/java/dev/tfp/integration/betterhud/HudValues*"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/BetterHudIntegration.java"
  - "paper/src/main/java/dev/tfp/paper/player/PlayerNeedsHudService.java"
  - "paper/src/test/java/dev/tfp/paper/player/PlayerNeedsHud*"
  - "paper/src/main/java/dev/tfp/paper/integration/HudSnapshot.java"
ui: true
in_game_steps:
  - "gamemode survival Bot; effect clear Bot; tfp debug hunger Bot 20; damage Bot 6; screenshot the health bar at GUI scale 2 and 3 within 1 s (hurt glow) and again after 3 s (glow stays while hurt with saturation > 0)"
  - "effect give Bot minecraft:absorption 60 1; screenshot: the fill includes absorption and the yellow surplus bar is filled; the health text includes absorption"
  - "effect give Bot minecraft:regeneration 30 0 at full health; screenshot the glow"
  - "tp Bot to a hot climate point (`tfp debug chunk` above 30 C) and wait until the thirst share from temperature is > 0; screenshot the tinted thirst bar at scale 2 and 3"
plan:
  - "Current (audit 2026-11-08): the two-layer bars (NP-HUD-02) show only background, fill and text. TFC's `renderHealthBar` additionally (a) fills by `(health + absorption) / max` and includes absorption in the text, (b) draws a yellow surplus bar `clamp(percent + absorption / 20 - 1, 0, 1)` over the bar, (c) draws the highlight overlay (overlay.png v=29) while hurt with saturation > 0, during `hurtTime`, or under Regeneration; `renderThirst` draws the overheat overlay at alpha `getThirstContributionFromTemperature() / MAX_TEMPERATURE_THIRST_DECAY`. The values behind the bars are parity targets (NP-HUD-02), so these states are too; only the art stays ours."
  - "Values: PlayerNeedsHudService publishes, from the player's own thread on the existing change events and its periodic refresh, `tfp_absorption` (TFC x50 scale), `tfp_health_glow` (0/1 from TFC's exact condition; hurtTime from the last damage tick, Regeneration from the effect, saturation from the TFC food data) and `tfp_thirst_overheat` stepped into N levels (N = the frames chosen below, from `ThirstState`'s temperature share); register them in BetterHudIntegration through HudValues. Health text and fill switch to health + absorption (keep `tfp_health_text` styles of PAR-HUD-17)."
  - "Art: a highlight variant layer of the health bar, a yellow surplus fill layer (same clip rule as the fill), and N pre-tinted overheat frames of the thirst bar, derived from the existing masters by `tools/images/hud_layers.py` (deterministic derivation, as the existing bg/fill layers) or, if a new master is needed, through the image-generation skill (raws under art/raw/hud-health-bar/ and art/raw/hud-thirst-bar/). No hand-drawn pixels."
  - "HudLayouts: the highlight layer shown while `tfp_health_glow` = 1, the surplus layer clipped by the surplus listener, the overheat frame chosen by `tfp_thirst_overheat`; the game-mode gate of PAR-HUD-12 applies to all of them; catalogue rows for the new placeholders and layers."
  - "Ledger: close the three divergence rows (or narrow each to the stepped alpha and our art)."
  - "Note: the state art is derived in tools/datagen HudArt at datagen time (ring recolour, fill re-hue, indexed PNG), deterministically from the existing bar resources, the same way as the mirrored plate; hud_layers.py and the art resources needed no change and no new master."
  - "Done: HudSnapshot.BarStates (absorption on the x50 scale, TFC surplusPercent, steady glow, hurt-time stamp, overheat level 0..4) published by PlayerNeedsHudService on each reading, on a hit (onDamage stamps the 10-tick hurt time, the glow ends at placeholder time) and after a potion effect change; HudValues: tfp_health number/listener and tfp_health_text include absorption, STATES tfp_absorption/tfp_health_glow/tfp_thirst_overheat and listener tfp_health_surplus registered by BetterHudIntegration; HudLayouts STATES: white ring while tfp_health_glow > 0, yellow surplus listener layer while tfp_absorption > 0, four pre-tinted thirst rings by tfp_thirst_overheat, all under the game-mode gate; health fill is now our tfp_health listener; catalogue rows and counts. Tests: PlayerNeedsHudServiceTest (+3), PlayerNeedsHudStatesMockBukkitTest (5), HudValuesBarStatesTest (5), HudLayoutsTest (+3), HudArtTest (+4). In-game check 2026-11-13, Paper 1.21.11 + 1.21.11 client, GUI scale 2 and 3: pass (docs/verification/PAR-HUD-19/). Ledger: the three divergence rows narrowed to our art, the stepped alpha and the 1 s refresh."
tests:
  - "paper: PlayerNeedsHudServiceTest: glow on hurt with saturation, on hurtTime, on Regeneration, off at full health without regeneration; absorption included in fill and text; overheat steps from the temperature share"
  - "integration/betterhud: HudValues for the three new placeholders"
  - "tools/datagen: HudLayoutsTest (layers present, gated, no overlap with armour, mount bar or air)"
done_criteria:
  - "Hurt glow, absorption fill + surplus bar and the thirst overheat tint appear in game at GUI scale 2 and 3 (screenshots reviewed) and are hidden in creative/spectator; tests green; ledger rows updated"
---

# PAR-HUD-19: Health and thirst bar states: hurt/regeneration glow, absorption in the fill and the yellow surplus bar, thirst overheat tint

> see_not_parity: NP-HUD-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
