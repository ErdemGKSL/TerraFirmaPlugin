---
id: PAR-HEAT-10
title: "Torches: burnout to dead torch, relight, wall torches"
area: HEAT
status: done
wave: 14
deps: [PAR-INFRA-11, PAR-INFRA-08, PAR-INFRA-13]
size: M
aliases: ["M3-HEAT-04 (torch)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCTorchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCWallTorchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/DeadTorchBlock.java"
  - "src/main/java/net/dries007/tfc/common/items/TorchItem.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/light/**"
  - "core/src/test/java/dev/tfp/core/light/**"
  - "core/src/test/resources/dev/tfp/core/light/**"
  - "paper/src/main/java/dev/tfp/paper/light/**"
  - "paper/src/test/java/dev/tfp/paper/light/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/light/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/light/**"
  - "content/generated/content/craftengine/tfp/configuration/light/**"
ui: false
in_game_steps: []
plan:
  - "Current: core/light burnout rules golden-tested."
  - "CE torch + dead torch (standing/wall) replacing vanilla torch placement for TFC torches; placement timestamp in block data; catch-up kind swaps to the dead torch after torchTicks; relight with firestarter/torch; torch item ages too (TFC TorchItem)."
  - "Note: TFC's TorchItem has no ageing at c3b4d9536 (it lights a weak fire on use, goes out in water and lights #lit_by_dropped_torch blocks when dropped); the use is ported here, the dropped-item behaviour is PAR-HEAT-22. The done criterion's 3 days is short of TFC's torchTicks (86,400 ticks = 3.6 in-game days of 24,000): a torch is still lit after 3 days in TFC too, so PAR-HEAT-21 checks it after 4 days."
  - "Deferred (PROTOCOL §3/§6.5): writing the CraftEngine entries needs the torch carriers in tools/datagen hitbox/HitboxDemands.java and the four ids in GeneratedCarrierTest's pinned list (both outside this item), so `light/TorchBlocks` is not yet called from Datagen; PAR-HEAT-21 adds the demands, wires the generator (configuration/light/torch.yml, models, torch_off texture, loot tables, names), and carries the in-game steps and the screenshot criterion."
  - "Done: core/light TorchRules (the four kinds and their swaps keeping the facing, afterCatchUp = TFC onRandomTick, burnoutTick, onFireStart reset/relight at any strength, onFireStop douse, useItemOn consume and stick lighting, dropped-torch batch and age constants); paper/light TorchDevice (device kinds tfp:torch and tfp:wall_torch, record = TFC TickCounterBlockEntity with TFC's Integer.MIN_VALUE initial counter; setPlacedBy stamps and queues the burnout callback; useItemOn always consumes, a #tfc:can_be_lit_on_torch item gives a torch; clicked or notified past the burn time hands it to the pipeline), TorchService (decide async, apply sync burnout pipeline: chunk-load catch-up over the chunk's lazy block index, the burnout callback re-armed by the calendar, re-validated commit; FireLightable relight; douse API for PAR-HEAT-13), PaperTorchEffects, TorchListener (TFC TorchItem.useOn: a held torch relights a dead torch through FireStarting WEAK instead of placing), TorchModule (service file after fire and charcoal). Datagen light/TorchBlocks (four blocks, two items, carriers as arguments; not wired, PAR-HEAT-21). Tests: core TorchRulesTest (8, incl. lazy vs TFC random ticks), paper TorchMockBukkitTest (16: catch-up after 4 days unloaded swaps to the dead torch, 3 days keeps it with the exact remaining wait, wall facing kept, relight restarts, fire start resets, relight between decision and commit wins, broken torch not revived, unstamped torch dies at its first check, stick lighting, use rules, neighbour update, douse, held torch on a dead torch, service-file order), datagen TorchBlocksTest (5). In-game check: PAR-HEAT-21."
tests:
  - "paper: MockBukkit catch-up swaps to dead torch"
  - "core: lazy-vs-stepped exists"
done_criteria:
  - "Deferred check tracked in PAR-HEAT-21"
---

# PAR-HEAT-10: Torches: burnout to dead torch, relight, wall torches

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
