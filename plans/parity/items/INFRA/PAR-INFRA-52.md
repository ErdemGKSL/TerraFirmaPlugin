---
id: PAR-INFRA-52
title: "Grill and pot block sounds in game: NETHER_WART hit 0.25/0.5 and one break 1.0/0.8 per player on 1.21.11 and 26.x"
area: INFRA
status: open
wave: 16
deps: [PAR-HEAT-08, PAR-HEAT-09]
size: S
aliases: ["moved from PAR-INFRA-23 done criterion 4 (deferred from PAR-INFRA-33 per PROTOCOL 6.5): the grill and pot half; the firepit half passed in PAR-INFRA-23 (docs/verification/PAR-INFRA-33/firepit-*.txt)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/GrillBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PotBlock.java"
files_owned:
  - "docs/verification/PAR-INFRA-52/**"
  - "docs/verification/PAR-INFRA-33/grill-pot-*"
ui: true
in_game_steps:
  - "Once PAR-HEAT-08 and PAR-HEAT-09 load tfp:grill and tfp:pot in CraftEngine (configuration/devices/): build the jar, `spikes/gradlew -p spikes/s2-destroy-stages jar`, start Paper 1.21.11 with EXTRA_JVM=-Dtfp.blocksound.log=true, our jar and the S2 spike plugin (docs/spikes/S2-destroy-stages.md §2; priming start, delete the world, start again), then repeat on 26.x"
  - "Two clients of the server's version: Bot (the breaker, ALSOFT_DRIVERS=null) and Viewer (a second player 3 blocks away, `mcclient.py launch <mc> --name Viewer`); a stone floor; `s2 place tfp:grill <x> <y> <z>` and `s2 place tfp:pot <x> <y> <z>` (or `/tfp debug place` from Bot's chat); Bot in survival with an empty hand mines each block with `xdotool mousedown 1` until it breaks"
  - "Read the server log: the [S2] sound lines for the dig and the break, interleaved with the [tfp-sound] SET|DROP lines"
plan:
  - "PAR-INFRA-33 rewrites CE block hit and break sounds to TFC's SoundType volume and pitch. The device sound type of the firepit, grill and pot is NETHER_WART (1.0/1.0): hit 0.25/0.5, break 1.0/0.8. PAR-INFRA-23 checked the firepit on 1.21.11 and 26.3 with a second viewer (docs/verification/PAR-INFRA-33/firepit-*.txt); the grill and pot were not loaded by CraftEngine at that time (their YAML sits outside configuration/ until PAR-HEAT-08/09), so this item holds their half of the check."
  - "Store the log excerpts as docs/verification/PAR-INFRA-33/grill-pot-<mc>-sound-log.txt and a verdict in docs/verification/PAR-INFRA-52/verdict.md."
tests:
  - "Log excerpts in docs/verification/PAR-INFRA-33/ and verdict.md in docs/verification/PAR-INFRA-52/"
done_criteria:
  - "Grill and pot: every hit while mining is minecraft:block.stone.hit at 0.25/0.5, and each break sends exactly one minecraft:block.nether_wart.break (or its replaced.* form for the viewer) at 1.0/0.8 to each player (a further replaced.* line to the breaker is paired with a [tfp-sound] DROP line), on 1.21.11 and 26.x; log excerpts in docs/verification/PAR-INFRA-33/"
---

# PAR-INFRA-52: Grill and pot block sounds in game: NETHER_WART hit 0.25/0.5 and one break 1.0/0.8 per player on 1.21.11 and 26.x

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
