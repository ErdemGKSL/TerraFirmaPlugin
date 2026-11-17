---
id: PAR-INFRA-56
title: "GUI button tooltips without the size/weight line: ItemSizeTooltipContributor skips items with the tfp:gui_button marker (firepit thermometer, pot, grill, barrel, powderkeg, knapping, tabs)"
area: INFRA
status: done
wave: 16
deps: []
size: S
aliases: ["found by the PAR-HEAT-20 reviews (fidelity and in-game, 85c731d): the firepit thermometer hover shows TFC's heat text plus a second line '⚖ Very Light ⇲ Very Small'"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/FirepitScreen.java"
  - "src/main/java/net/dries007/tfc/common/component/size/ItemSizeManager.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/itemsize/ItemSizeTooltipContributor.java"
  - "paper/src/test/java/dev/tfp/paper/itemsize/ItemSizeTooltipButtonTest.java"
ui: true
in_game_steps:
  - "op Bot; tfp debug firepitgui 400; hover the thermometer column at GUI scale 2: only the heat text ('Very Hot***'), no '⚖ ... ⇲ ...' line"
  - "Open a pot or barrel screen and hover its button slot: no size/weight line"
plan:
  - "Seen 2026-11-15 (PAR-HEAT-20 reviews, docs/verification/PAR-HEAT-07/03-debug-1200C-tooltip-gui2.png and the in-game re-run): every GUI button is a stand-in item (`GuiButtons.icon` / `GuiButtons.style`, PDC marker `tfp:gui_button`), and `ItemSizeTooltipContributor` (TFC `ItemSizeManager.addTooltipInfo`, the first line on every item) adds '⚖ <weight> ⇲ <size>' to it. TFC's screens draw button tooltips themselves, without item lines (`FirepitScreen.renderTooltip` shows only `heatTooltipStyle.formatColored(temperature)`). Ledger divergence row 'TFC's GUI buttons and the firepit thermometer show only their own tooltip' points here."
  - "Fix: in `contribute`, return without a line when `item.values()` holds the `tfp:gui_button` key (`GuiButtons.MARKER`; the packet copy's `PublicBukkitValues`). If other contributors turn out to add lines to button items too, skip buttons once in `TooltipPipeline` instead (then that file becomes part of this item's scope through a plan note)."
  - "When done: drop or reword the ledger divergence row (it becomes 'No' divergence) and add the after-capture to docs/verification/PAR-INFRA-56/."
  - "Done: ItemSizeTooltipContributor.contribute returns without a line when the packet copy's PublicBukkitValues hold the tfp:gui_button marker (GuiButtons.MARKER); no other contributor adds lines to buttons (food, fluid, heat need their own PDC records), so TooltipPipeline is untouched. Tests: paper ItemSizeTooltipButtonTest (3). Ledger divergence row reworded to 'No'. Game modes: tooltips are mode-independent (creative return strip unchanged). In-game check passed 2026-11-17 (Paper 1.21.11, vanilla client, GUI scale 2 and 3): thermometer hover shows only 'Very Hot***', barrel seal button and tank hovers show only their own text; docs/verification/PAR-INFRA-56/verdict.md."
tests:
  - "paper: ItemSizeTooltipButtonTest (a button icon gets no size line; the same material without the marker still gets it)"
done_criteria:
  - "The firepit thermometer hover shows only the temperature text at GUI scale 2 (in-game steps above); button slots of the other screens show no size/weight line"
---

# PAR-INFRA-56: GUI button tooltips without the size/weight line

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
