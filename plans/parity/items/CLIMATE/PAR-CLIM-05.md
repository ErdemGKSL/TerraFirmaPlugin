---
id: PAR-CLIM-05
title: "Seasonal grass tint: decide band resolution and make the colour change visible"
area: CLIMATE
status: done
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
  - "OWNER DECISION (2026-11-18, delegated to the manager: decide without asking): pick the cheapest of (a) more temperature bands, (b) a grass-only colour view, (c) month/phase-based bands that makes October grass warmer and yellower than June at the same site in the TFC direction; hard limits: keep the seasonal biome registry at no more than about twice the present variant count and keep the chunk remap async. Record the choice, the budget numbers and the evidence (June vs October screenshots at the same site and light) in docs/verification/PAR-CLIM-05/verdict.md and the ledger."
  - "BLOCKED on an owner decision (plans/gaps.md 'tasarım kararı'): 14.5 C and 5.1 C fall in the same band t1r1, so June and October look identical. Options: (a) more temperature bands (budget risk: 125 biomes x N), (b) a grass-only colour view, (c) month/phase-based bands."
  - "After the decision: change SeasonalBiomes + SeasonalColour to the new banding, keep the datapack under the registry budget, regenerate, re-run the controlled June/October comparison at (-768, 768) with `advance_weather false`."
  - "Record the remaining quantisation as a ledger divergence."
  - "Decision (2026-11-18): option (a), the cheapest that separates June and October: 7 temperature sample points every 32 colormap indices (t0, t0h, t1, t1h, t2, t2h, t3; the first layout's t0..t3 keep their index and name), edges half-way except the 0 C edge at index 153; 28 variants per biome, 3,500 in all (1.75x, client registry under 4,096, remap unchanged and async). At the tint site June 14.5 C is t1r1 #70a353, October 5.1 C t1hr1 #609c54. TFC direction note: TFC's grass colormap gets greener, not yellower, as it cools at a fixed groundwater (TFC #97b355 June, #689f54 October at the site's readings), so October is not yellower here in TFC either; we follow TFC (ledger row, verdict). Client snow temperature for the freezing bands needs DatagenTest: follow-up PAR-CLIM-26."
  - "Done: SeasonalColour 7 x 4 bands + SeasonalBiomes regenerated (3,500 variants); tests SeasonalColourTest 12, SeasonalBiomeRemapTest 14 (new juneAndOctoberShowDifferentBandsAtATemperateSite), DatagenTest unchanged and green; ./gradlew check green; decision, budget and expected colours in docs/verification/PAR-CLIM-05/verdict.md; in-game June/October screenshots left to the verify stage."
tests:
  - "core: SeasonalColourTest per band edges"
  - "paper: SeasonalBiomeRemapTest phases"
done_criteria:
  - "Controlled screenshots at the same site and light: June vs October grass differ in the TFC direction (October warmer/yellower)"
---

# PAR-CLIM-05: Seasonal grass tint: decide band resolution and make the colour change visible

> see_not_parity: NP-CLIM-01, NP-CLIM-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
