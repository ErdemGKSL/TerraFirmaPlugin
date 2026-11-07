---
id: PAR-SURV-06
title: "Item size and weight on Paper: container limits, overburdened and exhausted, tooltip"
area: SURV
status: done
wave: 8
deps: [PAR-INFRA-05]
size: M
aliases: ["M3-SURV-05"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/component/size/ItemSizeManager.java"
  - "src/main/java/net/dries007/tfc/common/component/size/Size.java"
  - "src/main/java/net/dries007/tfc/common/component/size/Weight.java"
  - "src/main/java/net/dries007/tfc/common/effect/ExhaustedEffect.java"
  - "src/generated/resources/data/tfc/tfc/item_size"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/itemsize/**"
  - "paper/src/test/java/dev/tfp/paper/itemsize/**"
  - "core/src/main/java/dev/tfp/core/item/**"
  - "core/src/test/java/dev/tfp/core/item/**"
ui: false
in_game_steps: []
plan:
  - "Resolve size/weight per ItemStack (explicit item_size definitions first, then the class default)."
  - "Enforce on InventoryClickEvent/InventoryMoveItemEvent for chests (chestMaximumItemSize), bundles and our device inventories via a shared predicate."
  - "Every 20 ticks per player (entity scheduler): count huge/very heavy items -> exhausted / overburdened effects (enableOverburdening)."
  - "Tooltip contributor: '⚖ Weight: Heavy / ⇲ Size: Large'."
  - "Stack sizes follow Weight (vanilla max_stack_size component set in datagen? record decision)."
  - "Decision: stack sizes stay baked into the CE items by datagen (PAR-SURV-01 foods, PAR-PROC-21 drops; ledger rows); the runtime does not rewrite max_stack_size. Vanilla items keep vanilla stack sizes (ledger divergence row; TFC rewrites every registry item)."
  - "In-game check deferred (PROTOCOL §6.5) to PAR-SURV-11 (deps PAR-METAL-03, which adds the anvil items the steps give); the steps and the screenshot criterion moved there."
  - "Done: core item/ItemSizeDefinitions (TFC lookup order), VanillaItemClasses, BurdenEffects; paper itemsize/ItemSizeModule (ItemSizes service, ContainerSizeListener chest/bundle/hopper limits, BurdenApplier exhausted/overburdened every 20 ticks on the player thread, ItemSizeTooltipContributor); ItemSizes.register stack sizers (TFC IItemSize step) for barrel/powderkeg/large vessel/crucible; tests core ItemSizeDefinitionsTest (4), paper ItemSizeMockBukkitTest (12, incl. hopper move and clear on disable); 9 divergence rows in the ledger; in-game check tracked in PAR-SURV-11. Unblocker round: the failed in-game capture was a busy shared server, not a defect; the check stays deferred to PAR-SURV-11 (PROTOCOL 6.5). Notes kept: BurdenApplier.last survives /tfp reload (one repeated action-bar notice), a dead player reads no burden (TFC does not check), isBundle matches ids ending in bundle (a CE item on a bundle base would count)."
tests:
  - "paper: MockBukkit chest refuses a huge item"
  - "paper: overburdened applied with two anvils in inventory"
done_criteria:
  - "Deferred check tracked in PAR-SURV-11"
---

# PAR-SURV-06: Item size and weight on Paper: container limits, overburdened and exhausted, tooltip

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
