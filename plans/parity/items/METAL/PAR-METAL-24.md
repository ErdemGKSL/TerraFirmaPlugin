---
id: PAR-METAL-24
title: "Crucible channel nubs: the crucible draws TFC's crucible_connection toward adjacent channels"
area: METAL
status: open
wave: 35
deps: [PAR-METAL-13]
size: S
aliases: ["follow-up of PAR-METAL-12 and PAR-METAL-13"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/CrucibleBlock.java"
  - "src/main/resources/assets/tfc/blockstates/crucible.json"
  - "src/main/resources/assets/tfc/models/block/crucible_connection.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/crucible/CrucibleBlock.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/crucible/CrucibleBlockTest.java"
  - "paper/src/main/java/dev/tfp/paper/crucible/CrucibleConnections.java"
  - "paper/src/test/java/dev/tfp/paper/crucible/CrucibleConnectionsTest.java"
ui: true
in_game_steps:
  - "crucible with a channel on one side and then two sides: the nub model shows toward each channel; screenshot"
plan:
  - "PAR-METAL-12 gave the crucible its four connection properties (east, south, west, north) but draws the shell model on every state, and nothing sets them. PAR-METAL-13 built the channels. Add a ShapeRule (as paper/channel/ConnectionRules) that sets each property when the neighbour on that side is a tfp:channel (TFC CrucibleBlock.updateShape), and draw TFC's crucible_connection model (turned by the blockstate's y per side) on the states that have a connection: the shell and the nub models merged per state in CrucibleBlock (16 models or item-display parts), the hitbox stays the shell (TFC's SHAPES[0] without nubs)."
tests:
  - "datagen: the 16 states draw the shell plus a nub per true side; paper: the rule sets and clears each side"
done_criteria:
  - "A crucible beside a channel draws TFC's connection nub toward it"
---

# PAR-METAL-24: Crucible channel nubs

Follow-up of PAR-METAL-12 and PAR-METAL-13. Follow `plans/parity/PROTOCOL.md`.
