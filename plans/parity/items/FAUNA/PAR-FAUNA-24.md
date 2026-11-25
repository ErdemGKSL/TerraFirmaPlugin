---
id: PAR-FAUNA-24
title: "Wild raids and armadillo scutes: fox berry picking, rabbit garden raids, calendar scutes"
area: FAUNA
status: open
wave: 44
deps: [PAR-FAUNA-09]
size: S
aliases: ["follow-up of PAR-FAUNA-09 (2026-11-25)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/prey/TFCFox.java"
  - "src/main/java/net/dries007/tfc/common/entities/prey/TFCRabbit.java"
  - "src/main/java/net/dries007/tfc/common/entities/prey/TFCArmadillo.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/wildraid/**"
  - "core/src/test/java/dev/tfp/core/wildraid/**"
  - "paper/src/main/java/dev/tfp/paper/wildraid/**"
  - "paper/src/test/java/dev/tfp/paper/wildraid/**"
ui: false
in_game_steps: []
plan:
  - "Current: PAR-FAUNA-09 removes the vanilla FOX_EAT_BERRIES and RABBIT_RAID_GARDEN goals; nothing raids TFC plants yet, and the armadillo keeps the vanilla scute timer."
  - "TFCFoxEatBerriesGoal (speed 1.2, range 12, vertical 1): a fox with an empty mouth walks to a fruiting block in tfc:fox_raidable, takes one product item and sets the bush to its picked state (through the berry module's public API; add one there through its owner if missing)."
  - "RaidGardenGoal (speed 0.7, range 16): a rabbit that wants food (moreCarrotTicks == 0) walks to farmland under a tfc:rabbit_raidable crop and destroys the crop with drops; moreCarrotTicks = 40, counted down by nextInt(3) per AI step (lazy: store the tick)."
  - "Armadillo scutes on the calendar: produceTicks = 30 TFC hours; drop a scute when the produced tick is older, spawn draw producedTick = now - nextInt(CALENDAR_TICKS_IN_HOUR * 20); suppress the vanilla scute drop (EntityDropItemEvent)."
tests:
  - "core: raid and scute rules golden tests"
  - "paper: MockBukkit goal transitions"
done_criteria:
  - "Goldens match TFC; the vanilla scute drop is suppressed for tfc:armadillo"
---

# PAR-FAUNA-24: Wild raids and armadillo scutes

> Claimed: sonnet-5.5 session_011osUp8, 2026-11-25

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
