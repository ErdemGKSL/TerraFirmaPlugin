---
id: PAR-HEAT-05
title: "Fire starting: firestarter use-duration, flint and pyrite, StartFireEvent world rules"
area: HEAT
status: done
wave: 12
deps: [PAR-INFRA-02]
size: M
aliases: ["M3-HEAT-02"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/FirestarterItem.java"
  - "src/main/java/net/dries007/tfc/common/items/FlintAndPyriteItem.java"
  - "src/main/java/net/dries007/tfc/util/events/StartFireEvent.java"
  - "src/generated/resources/data/tfc/tfc/fuel"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/fire/**"
  - "paper/src/test/java/dev/tfp/paper/fire/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/fire/**"
  - "core/src/main/java/dev/tfp/core/fuel/**"
  - "core/src/test/java/dev/tfp/core/fuel/**"
ui: false
in_game_steps: []
plan:
  - "Current: core/fuel FuelTable, FuelBurn, FireStart golden-tested."
  - "Firestarter: vanilla `consumable` with use time (or a bow-like use) tracked by PlayerInteractEvent + use-duration; on finish roll FireStart (fireStarterChance) at the target block."
  - "StartFireEvent: an SPI `FireLightable` that devices register (firepit pile, log pile, pit kiln, charcoal forge, torch, candle, lamp); otherwise place vanilla fire if canSurvive."
  - "Firepit creation: firestarter on a pile of 1 log + 3 sticks on the ground (placed items or groundcover) creates the firepit as TFC."
  - "CE items for the firestarter and flint & pyrite."
  - "Deferred (PROTOCOL §6.5): the firepit block is PAR-HEAT-06 (depends on this item), so the in-game steps, the self-test `fire.start` and the done criterion moved to PAR-HEAT-18."
  - "Done: core FireStartPlan (draws) and FirepitPile (TFC's item scan); paper/fire FireStarting service (decide async: rain gate snapshot, plan on the worker pool, apply on the region thread), FireLightable SPI (+ TNT), FirepitFactory hook for PAR-HEAT-06, FireStarterListener (firestarter = vanilla consumable 3.6 s bow, finish = cancelled consume; flint and pyrite on right click), PaperFireWorld fire tail with BlockIgniteEvent; datagen fire/FireStarterItems (tfp:firestarter, tfp:flint_and_pyrite, TFC firestarter sound, names). Tests: core FireStartPlanTest (4), FireStartGoldenTest unchanged, paper FireStartingMockBukkitTest (12). The firestarter and flint and pyrite workbench recipes are now emitted, so the one CraftingRecipesTest assertion that expected them pending on PAR-HEAT-05 now asserts the emitted recipe. In-game check: PAR-HEAT-18."
  - "Review fix: the real fire tail is tested (paper PaperFireWorldMockBukkitTest (7): top face lights the cell above, a side face lights the adjacent cell and leaves the log, a snow layer gets nothing, short grass is burnt and replaced, a cancelled BlockIgniteEvent changes nothing, an occupied cell gets nothing, the fluid gate); the shape reads MockBukkit lacks go through a package-private `Shapes` seam. The fluid gate now also sees seagrass, tall seagrass, kelp, kelp plant and bubble columns. PAR-HEAT-18's steps now drop the pile as item entities, aim at the top face of a grass/dirt block from 2+ blocks away and run `tfp debug block` as an opped Bot."
tests:
  - "paper: MockBukkit use-duration completes and calls the SPI"
  - "core: FireStartGoldenTest unchanged"
done_criteria:
  - "Deferred check tracked in PAR-HEAT-18"
---

# PAR-HEAT-05: Fire starting: firestarter use-duration, flint and pyrite, StartFireEvent world rules

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
