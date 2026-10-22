---
id: PAR-CLIM-05
title: "Seasonal grass tint: decide band resolution and make the colour change visible"
area: CLIMATE
status: blocked
wave: 14
deps: []
size: M
aliases: ["M2-SEA-01", "M2-SEA-02", "gaps: seasonal tint"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/TFCColors.java"
  - "src/main/resources/assets/tfc/textures/colormap"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/climate/Seasonal*"
  - "paper/src/test/java/dev/tfp/paper/climate/Seasonal*"
  - "core/src/main/java/dev/tfp/core/climate/SeasonalColour*"
  - "core/src/test/java/dev/tfp/core/climate/SeasonalColour*"
  - "tools/datagen/src/main/java/dev/tfp/datagen/SeasonalBiomes.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/SeasonalBiomes*"
  - "docs/porting/tint-investigation.md"
ui: true
in_game_steps:
  - "gamerule advance_weather false"
  - "weather clear"
  - "tfp time add months <n> until June; tfp time set noon; screenshot grass at (-768, 63, 768)"
  - "tfp time add months 4 (October); tfp time set noon; wait 10 s for the resend; screenshot the same view"
plan:
  - "BLOCKED on an owner decision (plans/gaps.md 'tasarım kararı'): 14.5 C and 5.1 C fall in the same band t1r1, so June and October look identical. Options: (a) more temperature bands (budget risk: 125 biomes x N), (b) a grass-only colour view, (c) month/phase-based bands."
  - "After the decision: change SeasonalBiomes + SeasonalColour to the new banding, keep the datapack under the registry budget, regenerate, re-run the controlled June/October comparison at (-768, 768) with `advance_weather false`."
  - "Record the remaining quantisation as a ledger divergence."
tests:
  - "core: SeasonalColourTest per band edges"
  - "paper: SeasonalBiomeRemapTest phases"
done_criteria:
  - "Controlled screenshots at the same site and light: June vs October grass differ in the TFC direction (October warmer/yellower)"
---

# PAR-CLIM-05: Seasonal grass tint: decide band resolution and make the colour change visible

> see_not_parity: NP-CLIM-01, NP-CLIM-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
