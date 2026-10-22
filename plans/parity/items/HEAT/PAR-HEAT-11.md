---
id: PAR-HEAT-11
title: "Candles, candle cakes and jack-o'-lanterns: burnout, lighting, carving"
area: HEAT
status: open
wave: 15
deps: [PAR-HEAT-10, PAR-INFRA-13]
size: S
aliases: ["M3-HEAT-04 (candles)", "M6-DECO-03 (candles, jack-o'-lanterns)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCCandleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCCandleCakeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/JackOLanternBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/light/candle/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/light/candle/**"
  - "content/generated/content/craftengine/tfp/configuration/light/candle/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:candle"
  - "place, light with firestarter"
  - "tfp time add days 5"
  - "screenshot"
plan:
  - "Candles (16 dyed + plain) and candle cakes with lazy burnout; pumpkin carving with a knife; jack-o'-lantern burnout."
tests:
  - "paper: catch-up tests"
done_criteria:
  - "Candle burns out after candleTicks (in-game with time add)"
---

# PAR-HEAT-11: Candles, candle cakes and jack-o'-lanterns: burnout, lighting, carving

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
