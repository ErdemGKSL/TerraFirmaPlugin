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
