---
id: PAR-METAL-21
title: "Forging tooltips: forging bonus with the smith's name, and 'Worked' on partly forged items"
area: METAL
status: open
wave: 31
deps: [PAR-METAL-05, PAR-INFRA-05]
size: S
aliases: ["parity audit 2026-11-13: ForgingBonusComponent.addTooltipInfo and ForgingCapability.addTooltipInfo named by no item"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/forge/ForgingBonusComponent.java"
  - "src/main/java/net/dries007/tfc/common/component/forge/ForgingBonus.java"
  - "src/main/java/net/dries007/tfc/common/component/forge/ForgingCapability.java"
  - "src/main/java/net/dries007/tfc/common/component/forge/Forging.java"
  - "src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java"
  - "src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java"
  - "src/main/resources/assets/tfc/lang/en_us.json"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/anvil/tooltip/**"
  - "paper/src/test/java/dev/tfp/paper/anvil/tooltip/**"
ui: true
in_game_steps:
  - "Forge a copper pickaxe head perfectly on the anvil as Bot (or the PAR-METAL-05 debug that completes a forge with a chosen bonus); hover at GUI scale 2: 'Perfectly Forged! by Bot' (green bonus, grey 'by'); screenshot"
  - "Hammer an ingot once on the anvil toward a plan and take it out; hover: 'Worked'; screenshot"
  - "Weld or craft the head into a pickaxe; hover: the bonus line is copied (tfc:copy_forging_bonus)"
plan:
  - "Current (audit 2026-11-13): PAR-METAL-05 stores the forging bonus and the forging steps on the item and PAR-TOOL-02 applies the efficiency/durability effects, but no item shows TFC's two tooltip lines: ForgingBonusComponent.addTooltipInfo (`tfc.enum.forgingbonus.<modest|well|expert|perfect>`, followed by the smith via `tfc.tooltip.author` '§a%s§7 by %s' when the component has an author) and ForgingCapability.addTooltipInfo (`tfc.tooltip.anvil_has_been_worked` 'Worked' while the item's forging steps are worked)."
  - "One TooltipContributor at TFC's place in onItemTooltip (after physical damage type, before glass working and food), reading the PDC records PAR-METAL-05 writes. If PAR-METAL-05 does not record the smith's name with the bonus, that is a defect in its files: add a follow-up item rather than editing core/forge or paper/anvil/forge."
  - "Lines are packet lore under the PAR-INFRA-05 pipeline (NP-CLIENT-02); colours as TFC's lang codes."
tests:
  - "paper: contributor lines for none/modest/well/expert/perfect, with and without author, worked vs fresh item; order next to heat and size lines"
done_criteria:
  - "Forged items show TFC's bonus and smith line; partly worked items show 'Worked'; tests green; screenshots pass review"
---

# PAR-METAL-21: Forging tooltips: forging bonus with the smith's name, and 'Worked' on partly forged items

> see_not_parity: NP-CLIENT-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
