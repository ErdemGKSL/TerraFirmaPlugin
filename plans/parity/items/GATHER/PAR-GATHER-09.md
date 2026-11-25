---
id: PAR-GATHER-09
title: "Natural logs and leaves: branch_direction on logs, persistent on leaves, set by tree generation and placement"
area: GATHER
status: open
wave: 24
deps: [PAR-GATHER-03, PAR-WORLD-11]
size: M
aliases: ["PAR-GATHER-04 leaf decay needs persistent too; found while doing PAR-GATHER-03: CE log and wood blocks only carry axis (TFC's blockstate JSON lists axis only), TfcTerrain.customTreeData places tfp:wood/log/<wood>[axis=..] without branch_direction, so no log is natural and tree felling (PAR-GATHER-03) never triggers"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/LogBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/BranchDirection.java"
  - "src/main/java/net/dries007/tfc/world/feature/tree/TreeHelpers.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/AssetPipeline.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/WoodBlocksTest.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/ce/templates/**"
ui: true
in_game_steps:
  - "Generate a forest; /tfp debug state on a trunk log: branch_direction is down (or a trunk_* value for a 2x2 tree), on a branch wood block it points at the log it hangs on"
  - "Break a trunk with tfp:metal/axe/copper: the whole tree falls (PAR-GATHER-03); a player-placed log (branch_direction=none) breaks alone"
plan:
  - "Current: the generated CE log, wood and stripped blocks have one property, axis. TFC's LogBlock also has BRANCH_DIRECTION (33 values, default none = player placed); worldgen sets it from the tree templates (TreeBlock.branch in core) and AxeLoggingHelper only fells blocks whose direction is not none."
  - "Datagen: add the branch_direction enum property (default none) to log, wood, stripped_log and stripped_wood without multiplying appearances (the variants keep matching on axis only, the others are wildcards); check the CE state budget and CeYamlCheck."
  - "TfcTerrain.customTreeData: append branch_direction=<TreeBlock.branch lowercase> to the log and wood refs and key the placeholder cache by it."
  - "Leaves (PAR-GATHER-04's LeafDecay only decays leaves that carry persistent=false): add the boolean property persistent (default false) to tfp:wood/leaves/<wood>, appearances unchanged; TfcTerrain places natural leaves with persistent=false; a leaf placed by a player gets persistent=true (TFC getStateForPlacement: persistent = placed by a player), through the CE placement behaviour or a BlockPlaceEvent fix-up."
  - "Keep stripped forms in step: PAR-GATHER-08's StripTable already copies branch_direction."
tests:
  - "datagen: the four wood block kinds list branch_direction with default none; appearances unchanged"
  - "datagen: the leaves list persistent with default false"
  - "paper: customTreeData ref carries the template's branch direction"
done_criteria:
  - "A generated tree's trunk logs carry their TFC branch direction and PAR-GATHER-03 fells it (screenshots reviewed); tests green"
---

# PAR-GATHER-09: Natural logs and leaves: branch_direction on logs, persistent on leaves, set by tree generation and placement

> Claimed: sapling worker, 2026-11-25

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
