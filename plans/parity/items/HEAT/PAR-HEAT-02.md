---
id: PAR-HEAT-02
title: "Heat tooltip in TFC colours and metal info lines (melting point, workable, weldable)"
area: HEAT
status: open
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
tests:
  - "paper: NBT-level tests per level colour and the work/weld lines"
done_criteria:
  - "Screenshot of a hot copper ingot tooltip matching TFC"
---

# PAR-HEAT-02: Heat tooltip in TFC colours and metal info lines (melting point, workable, weldable)

> see_not_parity: NP-CLIENT-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
