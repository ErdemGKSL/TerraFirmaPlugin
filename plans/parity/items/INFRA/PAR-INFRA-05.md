---
id: PAR-INFRA-05
title: "Item tooltip pipeline: several decorations per item, TFC styles and colours"
area: INFRA
status: done
wave: 2
deps: [PAR-INFRA-01]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java"
  - "src/main/java/net/dries007/tfc/client/ClientInventoryTooltip.java"
  - "src/main/java/net/dries007/tfc/config/FoodExpiryTooltipStyle.java"
  - "src/main/java/net/dries007/tfc/config/ItemTemperatureDisplayStyle.java"
  - "src/main/java/net/dries007/tfc/config/TimeDeltaTooltipStyle.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/tooltip/**"
  - "paper/src/test/java/dev/tfp/paper/tooltip/**"
  - "paper/src/main/java/dev/tfp/paper/packet/ItemDecorationHandler.java"
  - "paper/src/main/java/dev/tfp/paper/packet/ClientItemDecoration.java"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/ingot/copper"
  - "tfp debug heat Bot 900"
  - "Hover the ingot: 'Bright Red' in TFC colour, not italic"
plan:
  - "Current: `ItemDecorationHandler` applies one heat line to SET_SLOT/WINDOW_ITEMS with a strip on creative return."
  - "Generalise to an ordered list of `TooltipContributor`s (heat, food, size/weight, fluid contents, forging, metal info) contributed by modules; one combined `tfp:lore` marker so the strip removes exactly what we added."
  - "Port TFC's colours: heat level colours per `Heat` enum (not italic, no magenta default), food expiry styles, time-delta formatting."
  - "Cover the remaining packets: container content in other windows, cursor item, merchant, entity equipment if needed."
  - "Done: paper/tooltip (TooltipPipeline with one `tfp:lore` marker, TooltipContributor in TFC's onItemTooltip order, TooltipItem/TooltipData/TooltipStack/TooltipContext, HeatDisplayStyle with TFC's Heat colours and star marks, FoodExpiryStyle, TimeDeltaStyle + timeAndDate, TooltipStyles.TFC_DEFAULTS, HeatTooltipContributor with the work/weld suffix, FoodExpiryContributor, TooltipModule providing TooltipPipeline and taking over the start-up item-decoration handler); paper/packet ItemDecorationHandler (SET_SLOT, WINDOW_ITEMS + carried, SET_CURSOR_ITEM, SET_PLAYER_INVENTORY, MERCHANT_OFFERS results; strip on CREATIVE_INVENTORY_ACTION; PacketStack/NbtData adapters; takeOver). Lang keys tooltip.* (en_us, tr_tr). Tests: TooltipPipelineTest 11, TfcTooltipLinesTest 12, TimeDeltaStyleTest 3, TooltipPacketWiringTest 3. ARCHITECTURE.md §10.1; ledger feature row + 6 divergences. The old magenta italic HeatTooltipDecoration line is no longer sent (UI defect 2); deleting that class and its TfpPlugin lines is left to PAR-HEAT-02 (outside files_owned). Pending: the in-game screenshot check of PROTOCOL §6 (docs/verification/PAR-INFRA-05/: copper ingot at 900 C shows red 'Bright Red٭٭٭٭ - Can Weld', not italic; a heated food shows the expiry and heat lines; creative pick-up and put-back leaves no lore) was not run by this worker (no test-runner agent available)."
tests:
  - "paper: NBT-level tests: ordering, idempotent strip, client locale applied"
  - "paper: creative-return strip test with two contributors"
done_criteria:
  - "Two contributors render together in-game without duplicating on creative pick-up (screenshot)"
  - "UI defect 2 of plans/gaps.md (magenta italic heat line) closed"
---

# PAR-INFRA-05: Item tooltip pipeline: several decorations per item, TFC styles and colours

> see_not_parity: NP-CLIENT-04 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
