---
id: PAR-HUD-07
title: "HUD per MC version and TFC display styles: 26.x sprite list, XP bar style, health display style"
area: HUD
status: open
wave: 6
deps: [PAR-HUD-06]
size: S
aliases: ["X-HUD-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/config/HealthDisplayStyle.java"
  - "src/main/java/net/dries007/tfc/config/DisabledExperienceBarStyle.java"
  - "src/main/java/net/dries007/tfc/config/ClimateTemperatureDisplayStyle.java"
files_owned:
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hud/hidden-vanilla-sprites*.txt"
  - "docs/hud/versions.md"
ui: true
in_game_steps:
  - "Run the 26.x server and client; join; screenshot hotbar region"
plan:
  - "Hidden vanilla sprite list for 26.x (extract from the 26.x client jar), separate list per version family."
  - "Per-player style options are client config in TFC; offer server config equivalents (health text style, temperature unit) as a divergence."
tests:
  - "tools/datagen: per-version sprite list exists"
done_criteria:
  - "26.x screenshot shows no vanilla hearts/food"
---

# PAR-HUD-07: HUD per MC version and TFC display styles: 26.x sprite list, XP bar style, health display style

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
