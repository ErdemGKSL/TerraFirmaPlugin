---
id: PAR-GATHER-15
title: "Leaves placed by a player are persistent: TFC's getStateForPlacement sets persistent for a placed leaf block"
area: GATHER
status: done
wave: 27
deps: [PAR-GATHER-09]
size: S
aliases: ["found while doing PAR-GATHER-09: the persistent property exists on tfp:wood/leaves/<wood> (default false = natural), but no code sets persistent=true on a block a player places; the item/placement hook is outside GATHER-09's files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCLeavesBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/leaves/PlacedLeaves*.java"
  - "paper/src/test/java/dev/tfp/paper/leaves/PlacedLeaves*"
ui: false
in_game_steps: []
plan:
  - "A BlockPlaceEvent (MONITOR, region thread) for a tfp:wood/leaves/<wood> block placed by a player rewrites the state with persistent=true (TFC: PERSISTENT = context.getPlayer() != null). Fallen leaves that turn into a leaf block (FallenLeavesBlock, 8 layers) also place persistent=true. Dispensers and natural growth keep the default."
  - "Done: paper/leaves/PlacedLeaves listener registered in LeavesModule; PlacedLeavesTest (4) green with the leaves package tests. Fallen-leaves 8th layer to leaf block is not coded anywhere yet (noted in ledger). Pending in-game check (container cannot run client)."
tests:
  - "paper: a leaf block placed by a player is persistent, one placed by worldgen or a sapling is not"
done_criteria:
  - "Player-placed leaves never decay; natural leaves still can; tests green"
---

# PAR-GATHER-15: Leaves placed by a player are persistent

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
