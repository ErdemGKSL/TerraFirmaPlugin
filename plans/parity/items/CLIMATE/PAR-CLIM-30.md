---
id: PAR-CLIM-30
title: "Climate fog on vanilla clients: the client spike, foggy datapack variants and the seasonal remap hook"
area: CLIMATE
status: done
wave: 38
deps: [PAR-CLIM-14]
size: M
aliases: ["split from PAR-CLIM-14"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java"
  - "src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/climate/fog/**"
  - "paper/src/test/java/dev/tfp/paper/climate/fog/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/fog/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/fog/**"
  - "content/generated/content/datapack/tfp_fog/**"
ui: true
in_game_steps:
  - "tfp debug fog force; tfp time set hour 7; screenshot the view over open terrain at render distance 12"
  - "tfp time set hour 13; screenshot the same view (no fog)"
plan:
  - "Done: client-free part. datagen/fog/FogVariants (375 foggy variants tfp:fog/<biome>/l<1..3>, datapack tfp_fog, reference colour band, fog distances from ClimateFog planes at 12 chunks; attribute names minecraft:visual/fog_start_distance and fog_end_distance UNVERIFIED) wired into Datagen.generate; paper/climate/fog/FogRemap (pure hook: seasonal index + FogForecast level to foggy index and id; freezing bands skipped). Tests: FogVariantsTest (3), FogRemapTest (4), FogBudgetTest/FogForecastTest still pass. NOT done, no client in the container: the vanilla-client spike on 1.21.11 and 26.x, registering the datapack in TfpBootstrap and calling the hook from the packet handler (both stay off until the spike confirms the attributes), the screenshots; follow-up when a client is available: run the spike, then enable the datapack and the hook. content/generated/tfp_fog was not regenerated: generateContent already fails at the current HEAD for an unrelated reason (RenderedSound: no SoundType volume/pitch for the vanilla grass DeviceSound, from the soil/plant items), so rerun datagen once that is fixed. Pending in-game check (container cannot run client)"
  - "Run the spike on a 1.21.11 and a 26.x client: are biome environment attributes for fog distance honoured and can they be switched per chunk by the seasonal remap? Record the result in the ledger."
  - "If it passes: datagen writes foggy variants within the registry room (datagen/fog/FogBudget: one colour band per biome and up to three fog levels fit: 375 variants) and the remap uses FogForecast.level; Divergences row. If it fails: NP entry 'none, vanilla fog' after the owner approves."
tests:
  - "datagen: foggy variants present, registry size within FogBudget.room"
  - "paper: remap picks the foggy variant at 7 am on a foggy day and the base variant at 1 pm"
done_criteria:
  - "Foggy mornings appear on TFC's days and hours on a vanilla client (screenshots pass), or the spike result is recorded with an approved NP entry and Divergences row"
---

# PAR-CLIM-30: Climate fog on vanilla clients

Follow-up of PAR-CLIM-14. Follow `plans/parity/PROTOCOL.md`.
