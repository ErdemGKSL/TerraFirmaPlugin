---
id: PAR-CLIM-15
title: "Self-test weather.precip_sample and PrecipitationService in ModuleServices"
area: CLIMATE
status: done
wave: 12
deps: [PAR-CLIM-02]
size: S
aliases: ["follow-up of PAR-CLIM-02 (its itest case and service wiring need files outside its files_owned)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java"
files_owned:
  - "itest/src/main/java/dev/tfp/itest/cases/PrecipSampleCase.java"
  - "paper/src/main/java/dev/tfp/paper/TfpPlugin.java"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-CLIM-02 samples precipitation per player every 20 ticks (PrecipitationService.sampleOf) and decides any block with precipitationAt/isRainingAt; consumers find it through the static PrecipitationService.current(). The in-server case and the ModuleServices wiring were outside its files."
  - "itest case weather.precip_sample: with a forced climate (the self-test world's seed, a high cold column in a winter month and a low warm column in a summer month, calendar set through the CalendarService), check precipitationAt gives SNOW at the cold column and RAIN at the warm one at a rain time of OverworldClimate.rainIntensity, NONE under a placed roof (rainReaches false on the region thread) and in the Nether; add the case to SelfTestSuite."
  - "TfpPlugin.java: add only the one moduleServices.provide line. Provide PrecipitationService in ModuleServices next to the other plugin services, so modules (barrel rain fill PAR-PROC-09, fire starting PAR-HEAT-05, drinking in rain) look it up with services().require instead of the static holder."
  - "Done: itest weather.precip_sample (PrecipSampleCase, in SelfTestSuite; SelfTestSuiteTest counts 29) passed on Paper 1.21.11 via :itest:runSelfTest (column 0,0: SNOW at y 318 in DECEMBER, RAIN at y 128 in JULY, NONE under a stone roof and in world_nether); TfpPlugin provides PrecipitationService in ModuleServices; ledger rows updated, no new divergence."
tests:
  - "itest: weather.precip_sample"
done_criteria:
  - "weather.precip_sample passes on Paper 1.21.11 (spikes/run-matrix.sh or :itest:runSelfTest)"
---

# PAR-CLIM-15: Self-test weather.precip_sample and PrecipitationService in ModuleServices

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
