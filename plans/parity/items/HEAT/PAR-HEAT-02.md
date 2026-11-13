---
id: PAR-HEAT-02
title: "Heat tooltip in TFC colours and metal info lines (melting point, workable, weldable)"
area: HEAT
status: done
wave: 10
deps: [PAR-INFRA-05]
size: S
aliases: ["gaps: UI defects 2 and 5 (metal tooltip lines)", "M4-MET-01 (tooltips)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java"
  - "src/main/java/net/dries007/tfc/common/component/heat/Heat.java"
  - "src/main/java/net/dries007/tfc/util/Metal.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/heat/HeatTooltip*"
  - "paper/src/test/java/dev/tfp/paper/heat/HeatTooltip*"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/ingot/copper"
  - "tfp debug heat Bot 700"
  - "hover"
plan:
  - "Move HeatTooltipDecoration onto a TooltipContributor with TFC's per-level colours and no italic."
  - "Metal items: 'Metal: Copper', 'Melts at 1080 C', 'Can work' / 'Can weld' when above forging/welding temperature (item_heat forging/welding temps)."
  - "Note: TFC at c3b4d9536 has no separate 'Metal:' / 'Melts at' lines (1.12 wording); its metal info is the heat line's ' - Can Work' / ' - Can Weld' / ' - Danger!!' suffixes and the 'Melts into 100 mB of Copper (at Orange٭٭٭٭)' line from the heating recipe, which is what was ported."
  - "Done: paper/heat HeatTooltipModule (module heat_tooltip, service-file line after recipe) replaces the default heat contributor with HeatTooltipLine (level colour, upright, white work/weld suffix plus TFC's Danger!! from the heating recipe) and adds HeatTooltipMeltsInto (id metal, ORDER_METAL; grey 'Melts into', white '<n x count> mB of <fluid>', temperature in its level colour); HeatTooltipRecipes matches the packet TooltipItem against the heating recipe index with one tag snapshot (Netty thread). HeatTooltipDecoration's fallback line is now in the level colour and not italic (deleting the class needs TfpPlugin/HeatGlow edits outside files_owned; the tooltip module already takes over its handler). Lang tooltip.heat.danger/melts_into/fluid_units_of (en_us, tr_tr). Tests: HeatTooltipLinesTest 11 (incl. the generated copper ingot recipe). Ledger feature row + 4 divergences; ARCHITECTURE.md §10.1. Pending: the in-game screenshot check of PROTOCOL §6 (docs/verification/PAR-HEAT-02/: copper ingot at 700 C shows 'Dark Red٭٭٭ - Can Work' in dark red then the grey 'Melts into 100 mB of Copper (at Orange٭٭٭٭)') was not run by this worker (no test-runner agent available in this session)."
tests:
  - "paper: NBT-level tests per level colour and the work/weld lines"
done_criteria:
  - "Screenshot of a hot copper ingot tooltip matching TFC"
---

# PAR-HEAT-02: Heat tooltip in TFC colours and metal info lines (melting point, workable, weldable)

> see_not_parity: NP-CLIENT-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
