---
id: PAR-METAL-22
title: "Small vessel stores a mixed alloy: fills and melts write tfp:alloy, the vessel tooltip shows the mix"
area: METAL
status: done
wave: 28
deps: [PAR-METAL-01]
size: S
aliases: ["follow-up of PAR-METAL-01: the vessel files are owned by the done PAR-POT-04"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/mold/Vessel.java"
  - "src/main/java/net/dries007/tfc/util/FluidAlloy.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/vessel/Alloy.java"
  - "core/src/main/java/dev/tfp/core/vessel/VesselState.java"
  - "core/src/main/java/dev/tfp/core/vessel/VesselRules.java"
  - "core/src/test/java/dev/tfp/core/vessel/**"
  - "paper/src/main/java/dev/tfp/paper/vessel/**"
  - "paper/src/test/java/dev/tfp/paper/vessel/**"
  - "paper/src/main/java/dev/tfp/paper/mold/MoldPourer.java"
ui: true
in_game_steps:
  - "vessel holding 90 copper ingots' worth and 10 tin: heat it above 1080 (PAR-HEAT debug), hover: Contents: Bronze with the two metal lines; screenshot"
plan:
  - "Current: PAR-METAL-01 added the alloy display (core/alloy AlloyDisplay, paper/alloy AlloyPdc under tfp:alloy, AlloyTooltipContributor), but the small vessel (PAR-POT-04) still stores one fluid under tfp:fluid: VesselItems.settle keeps the first melted fluid and the total, and a fill of another metal only adds amount."
  - "Make the vessel's alloy a core FluidAlloy (fill per metal, TFC's capacity rule), write it with AlloyPdc (drop the single tfp:fluid read for vessels, keep a migration read of old items), use the result's specific heat and melt temperature (FluidHeat of the alloy result, tfp:metal/unknown for an unmatched mix), and let VesselTooltipContributor defer to AlloyTooltipContributor for items with tfp:alloy (no duplicate lines)."
  - "Crucible item: write its fluid content through AlloyPdc when PAR-METAL-12 adds the crucible."
  - "Done: core Alloy.ofMix, VesselRules.meltInto, VesselState.withAlloy; VesselItems writes tfp:alloy (mix, fill, drain, fluid = result, legacy tfp:fluid migrates), VesselTooltipContributor defers to the alloy tooltip, MoldPourer drains through VesselItems (files_owned widened to it). Tests: core VesselMixTest (5), paper VesselMockBukkitTest (+5, 14 total), MoldMockBukkitTest green. Pending in-game check (container cannot run client): the in_game_steps above, docs/verification/PAR-METAL-22/ not yet written."
tests:
  - "core: copper and tin melts give a bronze mix with the bronze heat values"
  - "paper: settle writes the mix; old tfp:fluid vessels still read; the tooltip shows each line once"
done_criteria:
  - "A vessel melted from 90 copper and 10 tin shows 'Bronze' with both metals, melts at the alloy's temperature, and tests are green"
---

# PAR-METAL-22: Small vessel stores a mixed alloy

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
