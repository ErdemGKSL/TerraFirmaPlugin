---
id: PAR-CLIM-14
title: "Climate fog: TFC's foggy mornings (ClimateModel.getFog) on vanilla clients through biome fog distance, or a recorded substitute"
area: CLIMATE
status: open
wave: 16
deps: [PAR-CLIM-06]
size: M
aliases: ["parity audit 2026-11-08: OverworldClimateModel.getFog and ClientForgeEventHandler.onFogRender named by no item or NOT-PARITY entry"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java"
  - "src/main/java/net/dries007/tfc/util/climate/ClimateModel.java"
  - "src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/climate/fog/**"
  - "core/src/test/java/dev/tfp/core/climate/fog/**"
  - "core/src/test/resources/dev/tfp/core/climate/fog/**"
  - "paper/src/main/java/dev/tfp/paper/climate/fog/**"
  - "paper/src/test/java/dev/tfp/paper/climate/fog/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/fog/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/fog/**"
  - "content/generated/content/datapack/tfp_fog/**"
ui: true
in_game_steps:
  - "tfp debug fog force (new: forces today's fog roll); tfp time set hour 7; screenshot the view over open terrain at render distance 12"
  - "tfp time set hour 13; screenshot the same view (no fog)"
plan:
  - "Current (audit 2026-11-08): nothing ports getFog. TFC rolls a foggy day once in FOGGY_DAY_RARITY (10) days from a calendar-day seeded random, with a strength fogModifier; the fog ramps in 4-6 am, holds 6-10 am, fades 10-12, and the client sets the terrain fog far plane to renderDistance * (1 - min(0.86, fog)) (near plane minus clamp(renderDistance / 10, 4, 64)); underwater fog also thickens at low light (0.6..1.0 by raw brightness, lerped)."
  - "core/climate/fog: pure port of getFog (seededRandom of total calendar days with salt 129341623413L, the ramp, the remaining scaling by climate as in the TFC source) with golden values."
  - "Spike first: check on 1.21.11 and 26.3 clients whether datapack biome environment attributes (visual fog start/end distance) are honoured by the client and can be switched per chunk by the existing seasonal biome remap (PAR-CLIM-06 packet path) without growing the registry beyond budget (a fog flag on the band variants, a few quantised fog levels). Record the measured result in the ledger."
  - "If the spike passes: datagen writes foggy variants (quantised levels) into `tfp_fog`; the seasonal remap picks the foggy variant while getFog > 0 at the player's calendar time and resends the view nearest first under the existing ResendLimiter budget; Divergences row for quantised levels and resend latency."
  - "If it fails: add an NP entry (section B, client rendering) with the substitute 'none, vanilla fog' after the owner approves, add the Divergences row, and close the item as done with that record. Underwater low-light fog is client-only in both cases (Divergences row)."
tests:
  - "core: FogGoldenTest (fog value per calendar day and hour vs a TFC scratch export at c3b4d9536, rarity 1 in 10)"
  - "tools/datagen (if built): foggy variants present, registry size within budget"
  - "paper (if built): remap picks the foggy variant at 7 am on a foggy day and the base variant at 1 pm"
done_criteria:
  - "Foggy mornings appear on TFC's days and hours on a vanilla client (screenshots pass), or the spike result is recorded with an approved NP entry and Divergences row"
---

# PAR-CLIM-14: Climate fog

> see_not_parity: NP-CLIM-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
