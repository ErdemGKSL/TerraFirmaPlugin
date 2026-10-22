---
id: PAR-INFRA-05
title: "Item tooltip pipeline: several decorations per item, TFC styles and colours"
area: INFRA
status: open
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
tests:
  - "paper: NBT-level tests: ordering, idempotent strip, client locale applied"
  - "paper: creative-return strip test with two contributors"
done_criteria:
  - "Two contributors render together in-game without duplicating on creative pick-up (screenshot)"
  - "UI defect 2 of plans/gaps.md (magenta italic heat line) closed"
---

# PAR-INFRA-05: Item tooltip pipeline: several decorations per item, TFC styles and colours

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
