---
id: PAR-DECO-04
title: "Chests, trapped chests and barrel racks per wood"
area: DECO
status: done
wave: 46
deps: [PAR-DECO-01, PAR-SURV-06, PAR-INFRA-13]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCChestBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCTrappedChestBlock.java"
  - "src/main/java/net/dries007/tfc/common/container/RestrictedChestContainer.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/chest/**"
  - "core/src/test/java/dev/tfp/core/chest/**"
  - "core/src/test/resources/dev/tfp/core/chest/**"
  - "paper/src/main/java/dev/tfp/paper/chest/**"
  - "paper/src/test/java/dev/tfp/paper/chest/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/chest/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/chest/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/chest/oak 2; place side by side; open"
plan:
  - "Per-wood chests on vanilla chest carriers (double chests work) with TFC textures; size restriction via PAR-SURV-06."
  - "Done 2026-11-25: the chest and the trapped chest of the 20 woods are CraftEngine device blocks (40 blocks x 12 states: facing, type single/left/right; ChestBlocks, configuration/chest/<wood>.yml), hitbox-only on minecraft:barrier with the chest as an item display of models built from vanilla's chest layers (ChestModels) over TFC's entity textures; TFC strength 2.5, wood sounds, axe, self drop, names in every TFC locale, items placing them. The plan's 'vanilla chest carriers' do not exist: PAR-INFRA-13 bans every chest state (client block entity renderer). Paper chest module: device kinds tfp:wood/chest and tfp:wood/trapped_chest (18 slots, ItemSizes.fitsChest from PAR-SURV-06, drop on break), ChestPlacement (vanilla placement and pairing, the other half follows, single when the partner is broken), ChestLink (18 or 36 slots, right half first as TFC's CompoundContainer), ChestScreens (two or four row GUI, one viewer per chest), right click rules. core/chest: ChestRules, ChestSide, ChestType, ChestState. Barrel racks: TFC has one barrel rack, tfp:barrel_rack, done in PAR-PROC-38 (no per-wood rack). Tests: ChestBlocksTest 6, ChestRulesTest 8, ChestMockBukkitTest 10. Lid animation, trapped chest redstone, waterlogging, a better carrier, hoppers, custom names, cat check, pests and the vanilla chest loot moved to PAR-DECO-26. Pending in-game check (no client in the build container)."
tests:
  - "paper: restriction"
done_criteria:
  - "A TFC oak double chest opens"
---

# PAR-DECO-04: Chests, trapped chests and barrel racks per wood

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
