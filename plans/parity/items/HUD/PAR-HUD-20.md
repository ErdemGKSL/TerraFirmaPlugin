---
id: PAR-HUD-20
title: "Ink splatter: the full-screen ink and glow-ink overlay while the player has tfc:ink / tfc:glow_ink (squid and octopoteuthis hits)"
area: HUD
status: open
wave: 43
deps: [PAR-DATA-07, PAR-FAUNA-11, PAR-HUD-12]
size: S
aliases: ["parity audit 2026-11-08: docs/hud/catalogue.md lists IngameOverlays.INK (renderInk) with 'none'; PAR-DATA-07 ports only the effects, NP-CLIENT-02 only their icons"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
  - "src/main/java/net/dries007/tfc/common/effect/TFCEffects.java"
  - "src/main/java/net/dries007/tfc/common/entities/aquatic/TFCSquid.java"
  - "src/main/java/net/dries007/tfc/common/entities/aquatic/Octopoteuthis.java"
  - "src/main/resources/assets/tfc/textures/misc/ink_splatter.png"
  - "src/main/resources/assets/tfc/textures/misc/glow_ink_splatter.png"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudLayouts.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudLayouts*"
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudArt*.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudArt*"
  - "docs/hud/catalogue.md"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudEffectValues.java"
  - "integration/betterhud/src/test/java/dev/tfp/integration/betterhud/HudEffectValues*"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/BetterHudIntegration.java"
  - "paper/src/main/java/dev/tfp/paper/hud/InkOverlay*.java"
  - "paper/src/test/java/dev/tfp/paper/hud/InkOverlay*"
ui: true
in_game_steps:
  - "gamemode survival Bot; tfp debug effect Bot tfp:ink 10 (or the PAR-DATA-07 command that applies the ink carrier); screenshot at GUI scale 2 and 3: the ink splatter covers the screen"
  - "same with tfp:glow_ink; screenshot the glow-ink splatter"
  - "effect clear Bot; screenshot: overlay gone; repeat with gamemode spectator: no overlay"
plan:
  - "Current (audit 2026-11-08): TFC's `renderInk` draws `textures/misc/ink_splatter.png` (or `glow_ink_splatter.png`) stretched over the whole screen at alpha 1 while the first-person player has `tfc:ink` (or `tfc:glow_ink`); the effects come from squid and octopoteuthis attacks. PAR-DATA-07 rides the effects on vanilla carriers and NP-CLIENT-02 replaces their inventory icons, but no item draws the splatter, so an inked player only sees the carrier's side effect."
  - "Values: `InkOverlayService` (feature module) tracks, from the player's own thread on EntityPotionEffectEvent and on join/respawn/world change, whether the player has our ink or glow-ink state (PAR-DATA-07's carrier plus our marker) and publishes `tfp_ink` (0 none, 1 ink, 2 glow ink) through `HudEffectValues`, registered in BetterHudIntegration."
  - "Art: migrate TFC's two splatter textures through the datagen (tfc-asset-migration, attribution in NOTICE) as BetterHud images; HudLayouts adds a full-screen layout (BetterHud screen-relative position and scale so it covers the GUI at every GUI scale; check the 1.21.x and 26.x HUD shader paths), shown while `tfp_ink` = 1 or 2, under the bars, behind the hotbar like TFC's `top` layer order allows. If BetterHud cannot stretch an image to the screen, tile or pre-scale the image per GUI scale and record the divergence."
  - "Game modes: hidden in spectator; TFC draws it only in first person, which the server cannot see: draw it in every camera mode and record that divergence."
  - "Ledger: feature row and the camera-mode divergence; catalogue row (`par-hud-20-ink`)."
tests:
  - "paper: InkOverlayServiceTest: effect added/removed/expired, respawn clears, spectator hides"
  - "integration/betterhud: HudEffectValues placeholder values"
  - "tools/datagen: HudLayoutsTest: the ink layout exists, is gated on tfp_ink and the game-mode gate"
done_criteria:
  - "An inked player sees TFC's splatter (ink and glow ink) at GUI scale 2 and 3 and it disappears when the effect ends (screenshots reviewed); tests green; ledger rows added"
---

# PAR-HUD-20: Ink splatter: the full-screen ink and glow-ink overlay while the player has tfc:ink / tfc:glow_ink (squid and octopoteuthis hits)

> see_not_parity: NP-HUD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
