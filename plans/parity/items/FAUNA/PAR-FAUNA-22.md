---
id: PAR-FAUNA-22
title: "Familiarity readout: sneak-looking at a TFC animal within 5 blocks shows its familiarity and pregnancy on the action bar (NP-UI-01 substitute for IngameOverlays.FAMILIARITY)"
area: FAUNA
status: open
wave: 42
deps: [PAR-FAUNA-05, PAR-FAUNA-21]
size: S
aliases: ["parity audit 2026-11-08: NP-UI-01 names PAR-FAUNA-05 for the familiarity action-bar text, but PAR-FAUNA-05's plan and done criteria do not build it; docs/hud/catalogue.md lists IngameOverlays.FAMILIARITY with 'none'"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
  - "src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.java"
  - "src/main/java/net/dries007/tfc/common/entities/livestock/MammalProperties.java"
  - "src/main/java/net/dries007/tfc/config/ClientConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/familiarity/**"
  - "core/src/test/java/dev/tfp/core/familiarity/**"
  - "paper/src/main/java/dev/tfp/paper/familiarity/**"
  - "paper/src/test/java/dev/tfp/paper/familiarity/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn cow adult female; feed it once (PAR-FAUNA-05) so familiarity > 0"
  - "As Bot sneak within 3 blocks while looking at the cow; screenshot the action bar at GUI scale 2"
  - "Make the cow pregnant (PAR-FAUNA-05 debug), sneak-look again; screenshot the pregnant line; step back to 7 blocks: the readout stops"
plan:
  - "Current (audit 2026-11-08): TFC's `renderFamiliarity` shows, while the player sneaks and the crosshair entity is a TFC animal with an adult familiarity cap > 0 within 5 blocks, a heart filled by familiarity (grey below 0.3, white from 0.3 when it stops decaying, red when an adult is at its cap) or the percentage (`displayFamiliarityAsPercent`), and `tfc.tooltip.animal.pregnant` for a fertilised pregnant mammal. NP-UI-01 decides this goes to the action bar; no item builds it."
  - "Core: a pure formatter in core/familiarity (familiarity, cap, age, pregnant -> colour band and text: TFC's three colour bands and thresholds, `%.2f` percent, the pregnant line with the animal's name)."
  - "Paper glue (FeatureModule): while a player is sneaking (PlayerToggleSneakEvent starts a per-player entity-scheduler task every 5 ticks, stopped on unsneak, quit or death), ray-trace the looked-at entity up to 5 blocks on the player's thread, read the animal's record through PAR-FAUNA-21's animal data API, and send the action-bar component only when its text changes or every 40 ticks (no packet spam); nothing when the target is not a TFC animal. Respect NP-UI-01's single channel: do not overwrite a newer action-bar message from another feature within its display time."
  - "Config key `animals.familiarity-as-percent` (TFC's client option as a server option, NP-CLIENT-04), default false: false shows a heart glyph bar (a font glyph through the CE font/image path, art through the image-generation skill if TFC's overlay slice cannot be reused) and the band colour; true shows the percentage."
  - "Ledger: feature row; divergences (action bar instead of a crosshair overlay; server-side sneak polling; server-wide option)."
tests:
  - "core: FamiliarityReadoutTest: bands at 0.29/0.30/cap, child vs adult at cap, percent text, pregnant line"
  - "paper: MockBukkit: sneaking at a cow in range sends one action bar; out of range or not sneaking sends none; text change re-sends; task stops on unsneak and quit"
done_criteria:
  - "Sneak-looking at a familiar animal shows TFC's familiarity state and the pregnant line on the action bar (screenshot reviewed); tests green; ledger rows added"
---

# PAR-FAUNA-22: Familiarity readout: sneak-looking at a TFC animal within 5 blocks shows its familiarity and pregnancy on the action bar (NP-UI-01 substitute for IngameOverlays.FAMILIARITY)

> see_not_parity: NP-UI-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
