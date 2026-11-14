---
id: PAR-HEAT-07
title: "Firepit GUI: fuel slots, input, outputs, temperature bar"
area: HEAT
status: done
wave: 14
deps: [PAR-HEAT-06, PAR-INFRA-03]
size: S
aliases: ["M3-HEAT-03 (GUI)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/FirepitScreen.java"
  - "src/main/java/net/dries007/tfc/common/container/FirepitContainer.java"
  - "src/main/resources/assets/tfc/textures/gui/fire_pit.png"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/firepit/gui/**"
  - "paper/src/test/java/dev/tfp/paper/firepit/gui/**"
ui: false
in_game_steps: []
plan:
  - "Textured GUI per docs/gui/catalogue.md `m3-heat-03-firepit`: fuel 4 at (8, 70-18i), input (80,29), outputs (71,57),(89,57), temperature bar overlay (these are TFC's points; the shipped layout moves the input and outputs onto the chest grid, chest slots 4, 21 and 23, ledger divergence)."
  - "Split (PROTOCOL 3a + 6.5): the art (TFC fire_pit.png re-laid out and the thermometer strip at the moved y) needs a datagen generator and its Datagen.java line, and opening the screen from the block replaces the stand-in in `paper/firepit/FirepitDevice.java`; both are outside files_owned, and the block is not loaded until PAR-HEAT-19. They, the in_game_steps and the screenshot criterion move to PAR-HEAT-20 (deps PAR-HEAT-07 and PAR-HEAT-19)."
  - "Done: paper `firepit/gui/`: `FirepitScreenLayout` (4-row chest = TFC's 176 x 186; DY 2 puts the fuel column on chest column 0, rows 3..0 = TFC slots 0..3; input chest slot 4 (row 0, x 80), outputs 21 and 23 (row 2, one column each side of the input); thermometer column = buttons 1/10/19/28; images `tfp:gui/firepit` and the 52-frame `tfp:gui/firepit_thermometer` at (30, 27); TFC `cascadeFuelSlots`), `FirepitSlotRules` (TFC `isItemValid`: fuel only in slot 3, heatables only in the input), `FirepitLink`/`FirepitSnapshot` (device contract), `FirepitScreens` (service: next-tick commit with the firepit's answer, reload every 10 ticks for the thermometer frame and cooked outputs, TFC slot limits, one viewer, gone firepit), `FirepitGuiListener` (one item per click on limit-1 slots, rest stays on the cursor), `DemoFirepitLink` + `FirepitGuiModule` (`/tfp debug firepitgui [temperature]`). Tests: FirepitScreensMockBukkitTest 11 (unused chest slots: place, shift-click, number-key swap, double-click, drag). Ledger row + 6 divergences (the input heat-capacity check added in the unblock round)."
  - "Unblock round: the review rounds accepted at 396f33a; the only failed check (in-game capture: `tfp:firepit is not a loaded custom block`) waits on PAR-HEAT-19 and is tracked in PAR-HEAT-20 under PROTOCOL 6.5. Applied the non-blocking notes: stale PAR-HEAT-06 stand-in ledger row now names PAR-HEAT-20, thermometer hover-strip clause, heat_capacity 0 divergence, wider unused-slot test, PAR-HEAT-20 ordering and Folia region note. Left for PROTOCOL upkeep: 6.5 deps wording."
  - "Verification: deferred check tracked in PAR-HEAT-20."
tests:
  - "paper: slot predicates (fuel only in fuel slots)"
done_criteria:
  - "Deferred check tracked in PAR-HEAT-20"
---

# PAR-HEAT-07: Firepit GUI: fuel slots, input, outputs, temperature bar

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
