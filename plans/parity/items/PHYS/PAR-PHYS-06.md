---
id: PAR-PHYS-06
title: "Isolated raw rock pops: a placed #breaks_when_isolated block with no supporting neighbour breaks and drops itself (WorldTracker isolated check)"
area: PHYS
status: done
wave: 6
deps: [PAR-INFRA-28, PAR-INFRA-09]
size: S
aliases: ["parity audit 2026-11-08: ledger divergence 'Raw/hardened rock drops the raw block (tfc:is_isolated loot condition) only when WorldTracker destroys it' says 'only the caller is missing ... (with the other WorldTracker work, PAR-PHYS-02)', but PAR-PHYS-02's plan and done criteria cover only collapses"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java"
  - "src/main/java/net/dries007/tfc/util/loot/IsIsolatedCondition.java"
  - "src/generated/resources/data/tfc/tags/block/breaks_when_isolated.json"
files_owned:
  - "core/src/main/java/dev/tfp/core/isolated/**"
  - "core/src/test/java/dev/tfp/core/isolated/**"
  - "paper/src/main/java/dev/tfp/paper/isolated/**"
  - "paper/src/test/java/dev/tfp/paper/isolated/**"
ui: true
in_game_steps:
  - "give Bot tfp:rock/raw/granite 4; tp Bot to a flat open area; setblock ~2 ~3 ~ air"
  - "As Bot place the raw granite against a block, then break the supporting block so the raw granite has no neighbour with collision; place a second raw granite floating against a torch or a flower only (no collision neighbour)"
  - "screenshot: the floating raw granite has popped and lies as a dropped raw granite item (not loose rocks); the supported one stays"
plan:
  - "Current (audit 2026-11-08): since PAR-INFRA-28 the 42 raw/hardened rock tables ship unchanged and the loot runtime evaluates `tfp:is_isolated`, but nothing ever destroys a block with `isolated` set, so the raw-block branch of those tables is dead and a player can float raw rock in the air."
  - "TFC: `onBlockPlace` (an entity places a block in `#tfc:breaks_when_isolated`, i.e. `#c:stones/raw`) queues the position in `WorldTracker`; on the next level tick each queued position is checked once: if the block is still in the tag and every one of the 6 neighbours has an empty collision shape (`isIsolated`), it is destroyed with `destroyBlockAndDropBlocksManually` and `TFCLoot.ISOLATED = true`, so the table drops the raw block itself."
  - "Port the rule in core/isolated (pure: the block's tag membership and the six neighbour collision flags decide break or keep). Paper glue in paper/isolated as a FeatureModule: BlockPlaceEvent (MONITOR, not cancelled) snapshots and enqueues the position; the decision runs off the event, and the region commit queue re-validates and breaks the block next tick through `LootService` with the isolated parameter set (decide async, apply sync; one check per placement, as TFC; nothing persisted, as TFC's buffered list is flushed every tick). Collision emptiness comes from the vanilla or CE carrier state (the PAR-INFRA-13 hitbox data), not from block names."
  - "Game modes: creative placement is queued too (TFC does not exempt it); the drop follows the gamerule doTileDrops like any block break."
  - "Ledger: update the is_isolated divergence row (caller implemented) and the PAR-INFRA-28 row's revisit."
  - "Done: core/isolated (IsolatedRule, IsolatedCell) + paper/isolated (IsolatedModule, IsolatedService: one AsyncDecisionPipeline per world over 7-cell NeighbourSnapshots, IsolatedListener: BlockPlaceEvent via a region task and BlockPhysicsEvent for TFC's onNeighborUpdate, IsolatedBlocks: commit re-checks owned neighbours, LootPopper: level event 2001, loot with isolated=true, doTileDrops/block_drops, removal); tests core IsolatedRuleTest (21), paper IsolatedMockBukkitTest (12); in game docs/verification/PAR-PHYS-06/ (pass); ledger feature row, is_isolated divergence closed, 3 new divergence rows (timing/no persistence, unloaded neighbour + commit re-check, BlockPhysicsEvent as neighbour notify)."
tests:
  - "core: IsolatedRuleTest: all six neighbours empty -> break; any neighbour with collision -> keep; block left the tag -> keep"
  - "paper: MockBukkit: placing raw granite with only air around breaks it on the next tick and drops tfp:rock/raw/granite; with a stone neighbour it stays; a cancelled place enqueues nothing"
done_criteria:
  - "A floating placed raw rock pops and drops itself in-game (screenshot reviewed); tests green; ledger rows updated"
---

# PAR-PHYS-06: Isolated raw rock pops: a placed #breaks_when_isolated block with no supporting neighbour breaks and drops itself (WorldTracker isolated check)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
