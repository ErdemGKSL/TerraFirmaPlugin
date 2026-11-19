---
id: PAR-CLIM-26
title: "Client snow look: freezing seasonal variants carry a below-0.15 biome temperature"
area: CLIMATE
status: done
wave: 15
deps: [PAR-CLIM-05]
size: S
aliases: ["follow-up of PAR-CLIM-05 (2026-11-18): the 0 C band edge exists, the client temperature mapping does not"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/mixin/BiomeMixin.java"
  - "src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/SeasonalBiomes.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/SeasonalBiomes*"
  - "tools/datagen/src/test/java/dev/tfp/datagen/DatagenTest.java"
ui: true
in_game_steps:
  - "gamerule advance_weather false"
  - "at a site whose January noon temperature is below 0 C: tfp time add months <n> until January; weather rain; wait 10 s for the resend; screenshot the sky and ground"
  - "tfp time add months 6 (July); weather rain; wait 10 s; screenshot the same view"
plan:
  - "PAR-CLIM-05 put a temperature band edge at TFC's 0 C index (153, `SeasonalColour.FREEZING_INDEX`; `SeasonalColour.isFreezing(band)` is true for t2, t2h, t3). The variants still copy the TFC biome's `temperature` (0.5 for all 125 biomes), so the vanilla client always draws rain (`Biome.getPrecipitationAt`: snow below 0.15). TFC draws snow at or below 0 C (`WeatherHelpers.getPrecipitationAt`, NOT-PARITY NP-CLIM-02)."
  - "Write `temperature: -0.5` (snow at every height) into the freezing variants in `SeasonalBiomes.variant`, keep the TFC value elsewhere; update `DatagenTest.seasonalBiomesAreOneVariantPerBiomeAndBandWithColormapColours`, whose `t2r3` fixture expects the TFC temperature 0.8 (t2r3 is a freezing band), to expect the snow value; add a datagen test that every non-freezing variant keeps the TFC temperature."
  - "Record the remaining gap (the band is sampled at the month's middle day at noon, not the instant) in the ledger divergence row on client snow."
tests:
  - "datagen: freezing variants carry a below-0.15 temperature, the others the TFC biome's"
  - "Done: freezing variants write temperature -0.5 (SeasonalBiomes.SNOW_TEMPERATURE); DatagenTest checks all 28 bands + base biome. In-game screenshot check not run (no headless client in this session); needs a follow-up run."
done_criteria:
  - "In a freezing month the client draws snow where the server snows; in a warm month rain; evidence in docs/verification/PAR-CLIM-26/"
---

# PAR-CLIM-26: Client snow look: freezing seasonal variants carry a below-0.15 biome temperature


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
