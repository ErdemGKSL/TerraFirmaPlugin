# Grass tint not changing with the season (M2-SEA-02), investigation

Date: 2026-10-19. No product code changed. Gap entry: `plans/gaps.md` (M2-SEA-02 tint lines).

## Code path (month change to client)

1. **Phase check.** `SeasonalBiomeService.start` runs `checkPhase` every `PHASE_CHECK_TICKS` (20) ticks. `checkPhase` compares `SeasonalBiomeRemap.phaseOf(clock)` with the stored phase (`SeasonalBiomeService.java:130`). On a change it clears the tables (`:135`), clears the resend cooldowns (`:136`) and calls `resendAround` for each online player (`:138`).
2. **Resend queue.** `resendAround` calls `request(key, true)` for every chunk in the player's view (`:152-160`). `request` sets `wantResend` (`:185`), skips if the chunk is already in flight (`:187`), otherwise computes the table on the CPU executor (`:192-194`).
3. **Store and offer.** The table is stored only if its phase is still current (`:197-201`). The `finally` block then calls `limiter.offer` if a resend was wanted (`:203-206`).
4. **Limiter.** `ResendLimiter.offer` refuses a key that was handed out less than `cooldownTicks` ago (`ResendLimiter.java:52`). `clearCooldowns` runs only at a phase change (`:75-77`).
5. **Drain.** The global tick drains up to 8 keys per tick and calls `World.refreshChunk` on the region thread (`SeasonalBiomeService.java:112-121`).
6. **Packet.** `SeasonalBiomeHandler.onSend` passes the packet unchanged when there is no table (`SeasonalBiomeHandler.java:94-96`). `table()` queues a computation on a miss (`SeasonalBiomeService.java:228-233`).

Answers to the step 1 questions:
- **Resend on month change only?** Resends are queued by a month change (`checkPhase`), by a packet miss (`table`, `biomesChanged`) and by `/fillbiome`. Chunk loads queue a table without a resend (`onChunkLoad`, `:257`, `resend=false`).
- **Loaded chunks keep the old biome?** Yes. The only refresh is the resend in step 5, and at a phase change only chunks in a player's view are queued.
- **Names match?** Yes. The packet side builds `tfp:seasonal/<biome>/t<T>r<R>` from `SeasonalBiomeRemap.variantIdOf` (`SeasonalBiomeRemap.java:109-112`) and looks each name up in the client registry (`SeasonalBiomeHandler.java:167`). The datagen writes the same names from `SeasonalColour.variantPath` (`tools/datagen/.../SeasonalBiomes.java:65`).

## Candidate cause: stale computation at a phase change (not confirmed)

1. A chunk's computation starts in the old phase `s`.
2. The phase changes. `checkPhase` clears the tables and the cooldowns (`:135-136`), and `resendAround` calls `request`, which finds the key in flight and returns early (`:187`).
3. The old computation finishes. `s != phase`, so its table is not stored (`:197`). The `finally` still calls `offer` (`:206`). This offer runs after `clearCooldowns`, so it is accepted, and the key is drained and recorded in `sent`.
4. The refresh packet goes out. `table(new phase)` finds no table, so the packet passes unchanged and a new computation starts (`:233`).
5. The new computation finishes and calls `offer` again (`:206`). `sent` still holds the key from step 3, so `offer` returns false (`ResendLimiter.java:52`). No second resend is queued.

Result: the client keeps the base (vanilla placeholder) biome of that chunk until the chunk is sent again for another reason.

## Log and run evidence

- **Screenshots (`scratchpad/shots/tint-summer.png`, `tint-autumn.png`).** The grass region has identical colour counts in both (4500, 3610, 1960 pixels). The dominant colour is `#678640`, and `#5a7537` is the second. Their hue equals vanilla plains grass `#91bd59` scaled by about 0.71 and 0.62. The TFC variant colours for this position are `#ead159` (June, band t1r2) and `#70a353` (October, band t1r1). None of the 16 band centres is within 36 of `#91bd59`. So the client showed the base biome in both shots.
- **Water.** Both bands have the same water colour at their centres (`#3c67a4`), so the small water change cannot come from the band.
- **tint-pair log (`spikes/run/tint-pair/logs/latest.log`).** No `Seasonal biomes:` warning (`SeasonalBiomeHandler.java:171-177` would print one if the variant ids were missing). The only commands are RCON `Set the time to 6000`, `Set the weather to clear` and `Teleported Bot`. No `/tfp time` command was issued, so the log does not record the month of either shot.
- **Saved calendar (`spikes/run/tint-pair/world/level.dat`, `tfp:calendar`).** `calendarTicks` = 4164367 decodes to October, day 6 (month index 9), at the stop at 04:45:55.
- **autumn6 (`scratchpad/autumn6/server.log`, `stats.py`).** No seasonal, remap, resend or refresh error. The grep for `tfp`, `seasonal` and `Exception` finds only startup lines and a Moonrise thread dump from village POI code. `stats.py` means differ across September, October and November (R-B 32.0, 26.5, -10.1), so the rewrite worked in that run.

## Most likely cause

The stale-computation race above (`SeasonalBiomeService.java:203-206` with `ResendLimiter.java:52`). It fits the base-biome colour in both shots and the absence of any log line. It is not proven: the run does not record the phase of each shot or the resend counters.

## What would confirm it

- **Unit test.** Drive `SeasonalBiomeService` through a phase change while a computation is in flight. This needs a fake `Scheduler`, a `WorldView` injected in place of `register(World)`, and a check that the key is refused. There is no injection point today, so the test needs a small package-private seam.
- **Server check (headless).** Use `/tfp debug` (`describe()`: `pendingResends`, `resent`) and `/tfp time add` to cross a month while standing still in loaded chunks. If the grass stays at the vanilla colour after the change, `pendingResends` is 0 and `resent` stops growing, the race is confirmed. A counter of refused offers in `ResendLimiter.offer` would show the refusal directly.
- **Fix to verify (not made).** When a finished computation is stale, re-request it for the current phase without calling `offer`. The new computation then offers the resend itself.

## Result (race reproduced and fixed in code; server check open)

- **Unit test.** `paper/src/test/java/dev/tfp/paper/climate/SeasonalResendRaceTest.java` drives a January computation through a phase change to February. Before the fix it failed on `pendingResends=1` (actual `pendingResends=0`, `tables=1`): the stale January `finally` offered the chunk after `clearCooldowns`, the drain put it into `sent`, and the February offer was refused by `ResendLimiter.offer`. The seam is `SeasonalBiomeService.registerView` (package-private, no production caller).
- **Fix.** `SeasonalBiomeService.java:209-218`. A stale computation (`s != phase`) no longer offers. It re-requests the chunk for the current phase, which keeps the resend flag, and the current-phase computation offers after its table is stored. Current-phase behaviour is unchanged.
- **Tests.** `dev.tfp.paper.climate.*` passes (ResendLimiterTest 6, SeasonalBiomeRemapTest 13, SeasonalResendRaceTest 1; 0 failures, 0 errors).
- **Still open.** The unit test shows the race in the limiter logic. It does not show that the grass tint changes in a running server. Close the gap with a headless month change in loaded chunks, watching the grass colour and `/tfp debug` (`pendingResends`, `resent`).

## Second check (2026-10-19): the run world had no TFC generator

Question: why does the `spikes/run/tint-fix` server show no seasonal change, and no "seasonal" log line at all?

- **Logs.** Neither log has a seasonal line on success, so the absence is not evidence. The service and the datapack discovery log only on failure (`TfpBootstrap.java:51-54`, `SeasonalBiomeHandler.java:171-175`). Both runs load PacketEvents and report `Packet layer: PacketEvents 2.14.0`, so the service starts in both (`TfpPlugin.java:453-462`, gated only on PacketEvents being loaded). The only startup difference that matters is the world generator, below.
- **Datapack.** `tfp_seasonal_biomes` is inside the plugin jar (2,132 entries under `content/datapack/tfp_seasonal_biomes/data/tfp/worldgen/biome/seasonal/<biome>/t<T>r<R>.json`), discovered by the bootstrap. It is not expected in `world/datapacks/` (that folder holds only `bukkit` in `tint-fix`). No "variants missing" warning was logged after the client joined, so the variants were found.
- **Cause.** `SeasonalBiomeService.register` (`SeasonalBiomeService.java:168-171`) builds a world view only when `world.getGenerator()` is a `TfcChunkGenerator`. `spikes/run/tint-fix/bukkit.yml` had no `worlds:` section, so the default world used vanilla terrain. Without a view, `resendAround` returns early (`SeasonalBiomeService.java`, `worlds.containsKey` check), no table is computed, and no chunk is resent. The working run folders (`spikes/run/wth3`, `verify2`, `firstjoin`, and others) and `spikes/run/install-fix/paper-1.21.11/bukkit.yml:44-46` all have `worlds: world: generator: TerraFirmaPlugin`. The grass seen in the tint-fix shots is vanilla plains, which fits.
- **Calendar.** The stored calendar in `tint-fix/world/level.dat` (`tfp:calendar`, decoded: calendarTicks 4070617 with 8 days per month) is October. The server reports `phase=OCTOBER`. A month change was never issued in the tint-fix sessions (no `/tfp` command in either log), so "June" and "October" cannot both have been sampled in one session.

Run-folder change (not production code): `spikes/run/tint-fix/bukkit.yml` gained the same `worlds:` block as install-fix (original saved at `scratchpad/tint-fix-bukkit.before.yml`). The server was restarted with `spikes/client/server.sh paper 1.21.11 spikes/run/tint-fix 25682` (RCON 25692). It started with `Done (24.843s)`, and `tfp debug packets` reports `seasonal biomes: phase=OCTOBER worlds=1 tables=0 computed=0`, so the service now has a TFC view. The chunks that already existed stay vanilla terrain, and the grass colour has not been re-measured with a client yet (open).

## Colormap check (fresh world, grass patch 100-200, 440-540)

Question: is the server's October grass colour the TFC grass colormap colour for 5.1 C and 186 mm, after vanilla shading?

Axis formula (copied from TFC, `src/main/java/net/dries007/tfc/client/TFCColors.java:306-307`, lookup `colorCache[temperatureIndex | (rainfallIndex << 8)]` at `:309`; the same arithmetic is in `SeasonalColour.java`, `temperatureIndex` and `rainfallIndex`):
- temperature index = `255 - clamp((int) ((t + 20f) * 255f / 50f), 0, 255)`, x of the colormap (column)
- rainfall index = `255 - clamp((int) (g * 255f / 500f), 0, 255)`, y of the colormap (row)
- Colormap: `assets/tfc/textures/colormap/grass.png` (256 x 256 RGB, TFC c3b4d9536). Pixel read as `(x = temperature index, y = rainfall index)`, which matches the flat index `t | r << 8` (the ColorMapReloadListener loads row-major pixels).
- Float arithmetic was emulated in float32 (`scratchpad/grass_check.py`).

| Reading | Temp index | Rain index | Colormap pixel (RGB) |
|---|---|---|---|
| June, 14.5 C, 186 mm | 80 | 161 | (151, 179, 85) |
| October, 5.1 C, 186 mm | 127 | 161 | (104, 159, 84) |

Server averages (`plans/gaps.md`, fresh world): June (46.6, 77.9, 43.2), October (49.3, 73.7, 48.5).

Ratio server / colormap per channel (R, G, B):
- June: 0.309, 0.435, 0.508
- October: 0.474, 0.464, 0.577
- October / June ratio per channel: 1.536, 1.065, 1.136

Comparison:
- The colormap changes from June to October mostly in red (R x0.69, G x0.89, B x0.99). The server changes R x1.06, G x0.95, B x1.12. The direction of the red change is opposite.
- The ratios are not equal across channels in either month, so vanilla shading is not a single scalar here, and the screenshot average also mixes shadowed and lit pixels. The per-channel ratio test therefore cannot cleanly prove the shade factor, but the change itself does not match: the colormap predicts a red-shifted, darker October, and the server shows a slightly brighter red and blue.

Result: MISMATCH. The server grass colour is not the colormap colour for this climate. The October colour is not explained by the TFC grass colormap at 5.1 C and 186 mm. Next open check: find out which colour the server applies to the grass (the biome variant band in use at the patch, its colour, and whether the client shows that variant), not the shading.

## Band check (datapack band for 5.1 C and 14.5 C at 186 mm)

Question: which seasonal datapack band does the server use for the two readings, and does the band's grass colour equal the TFC colormap colour for those readings?

Band selection (`core/.../climate/SeasonalColour.java`: `temperatureIndex`, `rainfallIndex`, `rainfallBand`; 4 temperature bands of 64 indices, rainfall edges `{0, 128, 179, 217}`):

| Reading | Temp index | Rain index | Temp band | Rain band | Variant |
|---|---|---|---|---|---|
| June, 14.5 C, 186 mm | 80 | 161 | 1 | 1 | `tfp:seasonal/<biome>/t1r1` |
| October, 5.1 C, 186 mm | 127 | 161 | 1 | 1 | `tfp:seasonal/<biome>/t1r1` |

Both readings fall in the same band, so the server gives the same variant in June and October at 186 mm. The band cannot show the June to October change.

Datapack fields (`content/generated/content/datapack/tfp_seasonal_biomes/data/tfp/worldgen/biome/seasonal/plains/t1r1.json`; identical to the copy in `content/build/resources/main`, checked with `diff -r`):
- `temperature`: 0.5, `downfall`: 0.5 (copied from TFC `plains.json`)
- `effects.grass_color`: 7381843 = `#70a353`. Set explicitly, so the colormap is not read for this variant.
- `effects.foliage_color`: 7115553, `water_color`: 3958692 (not used here).

The band colour is the colormap at the band centre (`centreTemperatureIndex` = 96, `centreRainfallIndex` = 153), not at the reading. `grass.png` at (96, 153) = `#70a353`, which matches the stored value.

Vanilla index check: the vanilla grass lookup (client `dvy.a(double,double,int[],int)`, read with `javap` from `dl/jar_1.21.11/client.jar`) computes `downfall *= temperature; x = (int)((1 - temperature) * 255); y = (int)((1 - downfall) * 255); index = y << 8 | x`. For the variant's own fields (0.5, 0.5) that is x = 127, y = 191, index 49023. This index is not used, because `effects.grass_color` is set and overrides the colormap. The override is standard vanilla behaviour; I did not disassemble the biome code that reads it.

Comparison with the TFC colormap at the exact readings (`grass.png`, TFC index as in the table above):

| Reading | TFC colormap | Server band colour `#70a353` | Difference (R, G, B) |
|---|---|---|---|
| June, 14.5 C, 186 mm (80, 161) | `#97b355` (151, 179, 85) | (112, 163, 83) | -39, -16, -2 |
| October, 5.1 C, 186 mm (127, 161) | `#689f54` (104, 159, 84) | (112, 163, 83) | +8, +4, -1 |

Conclusion: MISMATCH for June; close to October (within 8 per channel). The server colour is the band-centre colour, not the colour at the reading. Each temperature band spans 64 colormap indices (about 12.5 C) and the rainfall bands span 38 to 128 indices, so one band colour cannot follow the colormap within a band. The stored value is correct for its band, so the mismatch is quantisation, not a wrong colormap lookup. The 46.6 vs 49.3 difference in the fresh-world averages therefore cannot come from the grass band (both months use `t1r1`); it is lighting or shading.

Next step (not done here): either use a finer temperature band near the reading (for example 16 or 32 bands for the grass colour) or accept the quantisation and record it as a divergence in `docs/porting/ledger.md`.
