---
id: PAR-METAL-25
title: "Charcoal forge: melting into extra-slot molds, the grilled trait on outputs, picking up dropped fuel"
area: METAL
status: open
wave: 34
deps: [PAR-METAL-08, PAR-POT-05]
size: M
aliases: ["PAR-METAL-08 follow-up"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java"
  - "src/main/java/net/dries007/tfc/util/Helpers.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/charcoalforge/**"
  - "paper/src/test/java/dev/tfp/paper/charcoalforge/**"
  - "core/src/main/java/dev/tfp/core/charcoalforge/**"
  - "core/src/test/java/dev/tfp/core/charcoalforge/**"
ui: false
plan:
  - "PAR-METAL-08 left three pieces of TFC's `serverTick` out of `CharcoalForgeDevice`: (1) an input item whose heating recipe has a fluid result melts into the molds in extra slots 10 to 13 (`Helpers.mergeOutputFluidIntoSlot`: fill the mold, mix its temperature by heat capacity with `MoldPouring.mix`, the item is gone even with no mold); today it stays in its slot. (2) The output of a recipe that fires gets the `charcoal_grilled` food trait. (3) Every 20 ticks while lit, dropped items inside the box (x/z -0.2..1.2, y 0.875..1.25 around the forge) that are forge fuel go into the fuel slots (`gatherAndConsumeItems`)."
  - "Melting runs inside the lazy replay: `CharcoalForgeSim` already stops at the tick an item reaches its recipe temperature; give the converter a fluid variant and let `CharcoalForgeDevice.catchUp` pour into the extra-slot molds (`MoldItems.pourStep`) before replacing the slot. The gather runs on the region thread from the lit callback, never as a ticker."
tests:
  - "paper: a molten ingot fills the mold in an extra slot and the input slot empties; with no mold the item is lost as in TFC"
  - "paper: a dropped charcoal item above a lit forge goes into the first free fuel slot"
done_criteria:
  - "Divergence rows of PAR-METAL-08 for melting, the grilled trait and the fuel gather are removed"
---

# PAR-METAL-25: Charcoal forge: melting into extra-slot molds, the grilled trait on outputs, picking up dropped fuel

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
