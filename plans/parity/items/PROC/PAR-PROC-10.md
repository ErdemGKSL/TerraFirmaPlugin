---
id: PAR-PROC-10
title: "Log pile and charcoal pit"
area: PROC
status: done
wave: 13
deps: [PAR-INFRA-02, PAR-INFRA-10, PAR-HEAT-05, PAR-INFRA-13]
size: M
aliases: ["M3-PROC-04"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BurningLogPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/LogPileBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BurningLogPileBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/charcoal/**"
  - "core/src/test/java/dev/tfp/core/charcoal/**"
  - "core/src/test/resources/dev/tfp/core/charcoal/**"
  - "paper/src/main/java/dev/tfp/paper/charcoal/**"
  - "paper/src/test/java/dev/tfp/paper/charcoal/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/charcoal/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/charcoal/**"
  - "content/generated/content/craftengine/tfp/configuration/charcoal/**"
ui: false
in_game_steps: []
plan:
  - "Sneak-place logs to build a log pile (16 logs/block, inventory); light with fire; insulation check (all sides covered) via the neighbour runner; lazy conversion to charcoal piles (layers) via CharcoalPit; neighbour sweep with the 1-in-7 draw."
  - "Deferred (PROTOCOL 3 + 6.5): the CraftEngine blocks need hitbox rows and demands (HitboxDemands.java, tfc-shapes.json, GeneratedCarrierTest) outside this item's files_owned, so configuration/charcoal, the charcoal pile support rule, the in_game_steps and the screenshot criterion moved to PAR-PROC-37. PAR-PROC-37 depends on this item, not the reverse."
  - "Insulation check: the six sides are classified on the pile's region thread inside its device callback (TFC isValidCoverBlock), the sweep is the pure CharcoalSpread.sweep, and every world change it decides runs as a region task after the callback (decide, then apply)."
  - "Done: core/charcoal LogPileColumn (insertAndPushUp with suckLogsFromAbove, extractFromTop), LogPileData (click memory, TFC's 6 and 10 tick double clicks), BurningPileData (logs, lit tick), CharcoalPile (layer break, merge down, canSurvive), CharcoalSpread.randomTicks; paper/charcoal CharcoalPitDevice (kinds tfp:log_pile: 16 one-log slots of #tfp:log_pile_logs, plain-click extraction from the top pile, lost floor breaks and drops, fire next to it lights it; tfp:burning_log_pile: lit with the logs, sweep on light and per random tick, the top pile converts the column with the charcoal pile below after charcoalTicks), CharcoalListener (sneak log placement and insertion, charcoal layers, layer breaks, per game mode), PitClock (loaded burning piles, conversion due by calendar tick so a time skip converts, random ticks at vanilla's rate every 20 ticks), CharcoalModule (FireLightable for strong starts, chunk load and unload hooks), PaperCharcoalEffects. Tests: core LogPileColumnTest (13), paper CharcoalPitMockBukkitTest (14, incl. a covered lit pile of 16 logs becomes 6 charcoal layers after charcoalTicks). Ledger: feature row and 8 divergence rows. In-game check: PAR-PROC-37."
tests:
  - "paper: MockBukkit conversion after charcoalTicks"
  - "core: CharcoalPitGoldenTest exists"
done_criteria:
  - "Deferred check tracked in PAR-PROC-37"
---

# PAR-PROC-10: Log pile and charcoal pit

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
