---
id: PAR-CLIM-17
title: "Console /tfp debug chunk <x> <z> answers over RCON and reads precipitation through vanilla's rain gates"
area: CLIMATE
status: open
wave: 12
deps: [PAR-CLIM-02]
size: S
aliases: ["follow-up of PAR-CLIM-02 (its per-position readout lives in TfpPlugin/TfpCommands, outside its files_owned)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/TfpPlugin.java"
  - "paper/src/main/java/dev/tfp/paper/boot/TfpCommands.java"
  - "paper/src/test/java/dev/tfp/paper/boot/TfpCommandsReplyTest.java"
ui: false
in_game_steps: []
plan:
  - "Found in the PAR-CLIM-02 in-game re-check (2026-11-13, commit 1942d21): `tfp debug chunk <x> <z>` over RCON returned an empty reply for non-zero coordinates, so the per-position precipitation readout could not be produced; the client typing coordinates got `Incorrect argument`."
  - "Suspected cause (verify first): `TfpPlugin.debugChunkAt` finishes on the cpu executor (`PlayerClimateService.readAt`, `PrecipitationService.readAt`), and `TfpCommands.reply` never waits on Paper's main thread (`callerMayWait()` is false there, PAR-INFRA-38), so the reply is sent through `whenComplete` after RCON has already returned its response. A chunk the main thread does not own (`scheduler.isOwner` false) adds a region hop that delays it further."
  - "Fix: when the console runs on a tick thread that owns the column's region, do the whole readout inline (the column read, then the pure climate and `precipitationAt` decision; cheap) and return a completed future, so `reply` answers at once. Keep the async path for Folia and for unowned chunks, and say in the reply when the console cannot get the result in time (log it)."
  - "Fidelity: `debugChunkAt` reads at the WORLD_SURFACE top + 1 and passes `rainReaches = true`. Use `PrecipitationService.rainReaches(world, x, y, z)` on the region thread (MOTION_BLOCKING top and sky light 15), as TFC's `isRainingAt` does, so a column under leaves or glass reads NONE."
  - "Reproduce the client's `Incorrect argument` (for example a decimal or `~` coordinate given to `IntegerArgumentType`); accept `~` relative coordinates for a player sender if that is the cause."
tests:
  - "paper: MockBukkit test that `/tfp debug chunk <x> <z>` from the console on the primary thread gets the precipitation line synchronously"
  - "paper: the console readout under a roof reads NONE"
done_criteria:
  - "Over RCON on Paper 1.21.11, `tfp debug chunk -600 0` returns the climate and `precipitation here:` lines in the RCON response"
---

# PAR-CLIM-17: Console /tfp debug chunk <x> <z> answers over RCON and reads precipitation through vanilla's rain gates

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
