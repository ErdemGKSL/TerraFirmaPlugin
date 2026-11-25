---
id: PAR-METAL-28
title: "Anvil remote work: TFC AnvilBlockEntity.workRemotely for the trip hammer (the AnvilRemote service)"
area: METAL
status: open
wave: 55
deps: [PAR-METAL-05]
size: S
aliases: ["follow-up of PAR-MECH-07 (2026-11-25): the anvil's code is outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/anvil/forge/**"
  - "paper/src/test/java/dev/tfp/paper/anvil/forge/**"
ui: false
in_game_steps: []
plan:
  - "PAR-MECH-07's trip hammer asks `dev.tfp.paper.triphammer.AnvilRemote` (from `ModuleContext.services()`, AnvilRemote.NONE until provided) for `workRemotely(anvil, ForgeStep.HIT_LIGHT, 12, true)` on the metal anvil in front of it, on the anvil's region thread, and damages its hammer when it returns true."
  - "Port TFC `AnvilBlockEntity.workRemotely(step, movement, forceCompletion)` beside `AnvilForger.work`: no player, no hammer of the anvil (the trip hammer carries its own); false when the input has no forging, when the first hit would overwork (`!isWorked && work == 0 && movement < 0`), when the recipe no longer matches the anvil's tier or the input is too cold; forceCompletion turns and clamps the movement so the cursor never passes the target (`computeTarget`); `addStep(step, movement)`; work outside 0..LIMIT destroys the input (anvil destroy sound); else the forging effects (hit sound, particles), and on `checkComplete` the result with the input's heat kept (no forging bonus: TFC's remote work names no player). Edit the anvil record through `DeviceService.edit` and keep an open anvil screen in step (the AnvilForgeModule access hook)."
  - "Provide it from AnvilForgeModule: `context.services().provide(AnvilRemote.class, ...)`."
tests:
  - "paper: workRemotely on a hot bronze ingot with a recipe moves the cursor by 12 toward the target and stops at it, refuses cold or unworkable inputs, completes the recipe, destroys past the limit"
done_criteria:
  - "The trip hammer's light hit works a hot ingot on a metal anvil (MockBukkit; in game with PAR-MECH-27)"
---

# PAR-METAL-28: Anvil remote work

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
