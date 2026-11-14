---
id: PAR-PROC-10
title: "Log pile and charcoal pit"
area: PROC
status: blocked
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
ui: true
in_game_steps:
  - "sneak-place 16 logs"
  - "cover with dirt, light top"
  - "tfp time add hours 20"
  - "dig in; screenshot"
  - "right-click a furnace and a log pile with charcoal (no pile; the furnace opens, the log pile gives a log)"
plan:
  - "Last review (rejected after 2 repairs, 2026-11-14; stays blocked on PAR-PROC-37): both reviews of ebf0f07 ACCEPT the blocked state (tests green: core 2896, paper 1120, datagen 424; :dist:shadowJar builds). The in-game capture did NOT run (another agent's server held the only slot; no screenshots or .webp), and at this commit it could only show the vanilla-log fallback, because tfp:log_pile, tfp:burning_log_pile and tfp:charcoal_pile are not generated (hitbox carrier rows missing, PAR-PROC-37). Open non-blocking notes for the next worker: (1) say explicitly who closes this item after PAR-PROC-37 passes (add this file and its STATUS.md row to PAR-PROC-37's hubs, or the loop/manager closes it); (2) the STATUS.md Totals line does not match the rows (table: 6 blocked, 140 done, 199 open), regenerate it; (3) capture in game once PAR-PROC-37 lands: spikes/client/server.sh paper 1.21.11 with an own run dir and free port, session.sh to join, snow_block test room with light blocks at noon and clear weather, rcon.py steps (sneak-place 16 logs, dirt cover, light top, tfp time add hours 20, dig in), shot.sh at guiScale 2 (3 for HUD/GUI), lossless WebP via python3 -I PIL, stop your own server by PID."
  - "Blocked on PAR-PROC-37 (PROTOCOL 3b, review of 63be09e, 2026-11-14): the done criterion needs the CraftEngine blocks tfp:log_pile, tfp:burning_log_pile and tfp:charcoal_pile. They are non-full-cube CE blocks, so GeneratedCarrierTest requires their carriers from HitboxDemands.java and tfc-shapes.json rows (tfc:log_pile / tfc:burning_log_pile[axis,count]), which are outside this item's files_owned; without them CharcoalPitDevice.placePile returns false (services.exists) and the sneak click falls through to a vanilla log (ledger Divergences row). PAR-PROC-37 owns the hitbox rows, datagen/charcoal, configuration/charcoal and docs/verification/PAR-PROC-10 and runs the in_game_steps; once its check passes, set this item done with that verdict (closing it is an explicit allowance for PAR-PROC-37: its commit may set this file's status, add the Done plan line and change this item's STATUS.md row; the 63be09e reviews accepted that this item edited PAR-PROC-37.md once, to drop the dep cycle). The criterion stays on this item (no 6.5 deferral: the check waits on this item's own blocks, not on dependents)."
  - "Sneak-place logs to build a log pile (16 logs/block, inventory); light with fire; insulation check (all sides covered) via the neighbour runner; lazy conversion to charcoal piles (layers) via CharcoalPit; neighbour sweep with the 1-in-7 draw."
  - "Follow-up (PROTOCOL 3a): the CraftEngine blocks need hitbox rows and demands (HitboxDemands.java, tfc-shapes.json, GeneratedCarrierTest) outside this item's files_owned, so the blocks, the charcoal pile support rule and the in-game run are PAR-PROC-37. PAR-PROC-37 does not list this item in its deps (the runtime is already in the tree); this item waits for it (blocked)."
  - "Insulation check: the six sides are classified on the pile's region thread inside its device callback (TFC isValidCoverBlock), the sweep is the pure CharcoalSpread.sweep, and every world change it decides runs as a region task after the callback (decide, then apply)."
  - "Unblocker pass (2026-11-14, after the accepted blocked state of ebf0f07): no blocking review note remains; the only open criterion is the in-game one, which needs PAR-PROC-37's blocks (a size-M item with its own files_owned, open in wave 14), so this item stays blocked. Fixed here: the ledger fallback row now states TFC's sturdy-face condition and the charcoal sneak click, and the MockBukkit test count (17)."
  - "Implemented (runtime, 405ecc2 + 63be09e): core/charcoal LogPileColumn (insertAndPushUp with suckLogsFromAbove, extractFromTop), LogPileData (click memory, TFC's 6 and 10 tick double clicks), BurningPileData (logs, lit tick), CharcoalPile (layer break, merge down, canSurvive), CharcoalSpread.randomTicks; paper/charcoal CharcoalPitDevice (kinds tfp:log_pile: 16 one-log slots of #tfp:log_pile_logs, plain-click extraction from the top pile, lost floor breaks and drops, fire next to it lights it; tfp:burning_log_pile: lit with the logs, sweep on light and per random tick, the top pile converts the column with the charcoal pile below after charcoalTicks), CharcoalListener (sneak log placement and insertion, charcoal layers, layer breaks, per game mode), PitClock (loaded burning piles, conversion due by calendar tick so a time skip converts, random ticks at vanilla's rate every 20 ticks), CharcoalModule (FireLightable for strong starts, chunk load and unload hooks), PaperCharcoalEffects. Tests: core LogPileColumnTest (13), paper CharcoalPitMockBukkitTest (17, incl. a covered lit pile of 16 logs becomes 6 charcoal layers after charcoalTicks). Ledger: feature row and 8 divergence rows. In-game check: not yet run with the blocks (in-game run at 63be09e: no CE log pile, a vanilla log placed, REJECT); runs in PAR-PROC-37."
tests:
  - "paper: MockBukkit conversion after charcoalTicks"
  - "core: CharcoalPitGoldenTest exists"
done_criteria:
  - "A covered lit log pile becomes charcoal in-game"
---

# PAR-PROC-10: Log pile and charcoal pit

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
