---
id: PAR-INFRA-60
title: "Device automation: hoppers, droppers and pipes insert into and pull from CE device blocks by side (TFC SidedHandler, *EnableAutomation), wired for the done devices"
area: INFRA
status: done
wave: 18
deps: [PAR-INFRA-02, PAR-INFRA-18, PAR-PROC-04, PAR-PROC-08, PAR-PROC-11, PAR-HEAT-06, PAR-HEAT-08, PAR-HEAT-09, PAR-PROC-18]
size: M
aliases: ["parity audit 2026-11-17: ledger divergences 'TFC seals and unseals a barrel on a redstone signal ... and exposes automation sides' (revisit: 'redstone, gathering and automation follow-ups'), the firepit/grill row (`firePitEnableAutomation`, `grillEnableAutomation` hopper sides) and the composter output row; no item owns hopper automation of TFC devices"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/capabilities/SidedHandler.java"
  - "src/main/java/net/dries007/tfc/common/capabilities/PartialItemHandler.java"
  - "src/main/java/net/dries007/tfc/common/capabilities/InventoryItemHandler.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/FirepitBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/GrillBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/PotBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/PowderkegBlockEntity.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/device/automation/**"
  - "core/src/test/java/dev/tfp/core/device/automation/**"
  - "paper/src/main/java/dev/tfp/paper/device/automation/**"
  - "paper/src/test/java/dev/tfp/paper/device/automation/**"
  - "itest/src/main/java/dev/tfp/itest/cases/DeviceAutomationCase.java"
  - "docs/verification/PAR-INFRA-60/**"
ui: true
in_game_steps:
  - "op Bot. tfp debug place tfp:quern at ~2 ~ ~ with a handstone; a hopper above it holding 8 minecraft:bone (TFC quern recipe bone_meal); a hopper below; tfp debug quern: the input fills from above, the output goes down after a grind"
  - "tfp debug place tfp:firepit lit; a hopper on its side holding 4 tfp:wood/log/oak: the fuel slots fill (firePitEnableAutomation); screenshot the firepit GUI"
  - "tfp debug place tfp:wood/barrel/oak; a hopper on the side with a water bucket and one above with 4 salt; tfp debug barrel: the bucket went through the fluid-container in/out slots and the item slot holds the salt"
  - "tfp debug place tfp:composter ready; a hopper below pulls the compost; tfp debug compost"
plan:
  - "What exists: every TFC device block entity exposes an item handler per face when its `*EnableAutomation` server config is on (default true): `SidedHandler.on(PartialItemHandler(inventory).insert(slots...).extract(slots...), faces)`. Our devices are CE blocks over non-container vanilla carriers, so vanilla hoppers and droppers do not see them; only the composter's output has a partial rule (ledger). No device takes hopper input or gives hopper output."
  - "1. core/device/automation: the per-kind side tables as pure data ported from each block entity's constructor (which slots insert/extract on which faces, facing-relative for the barrel), plus the insert/extract decision through the kind's slot filter (DeviceInventory rules). Golden tests per kind against the TFC constructors."
  - "2. paper/device/automation: Paper's HopperInventorySearchEvent supplies a virtual Inventory for a hopper or hopper minecart whose source or destination is a device block (DeviceService.inspect), backed by DeviceService.edit on the device's region thread: the hopper move becomes one edit (snapshot, decide, commit), never a stored live inventory. Droppers through BlockDispenseEvent/InventoryMoveItemEvent where the target is a device. Respect TFC's per-kind defaults (barrel, firepit/grill/pot, quern, composter, powderkeg) and leave a registration API for the devices still to come (crucible, charcoal forge, blast furnace, loom, large vessel, crate, nest box, firebox, powder bowl): their items call `DeviceAutomation.register(kind, sides)`."
  - "3. Config keys (barrelEnableAutomation, firePitEnableAutomation, quernEnableAutomation, composterEnableAutomation, powderKegEnableAutomation) read with TFC defaults through a supplier; the config.yml entries are PAR-DATA-06's. Fluid sides (barrel and pot SidedHandler<IFluidHandler>) are recorded as a divergence: no vanilla block moves fluids."
  - "4. Threading: the hopper event runs on the hopper's region thread, which owns the adjacent device block on Folia (same region); assert it, and refuse (no move) across a region border."
  - "Verified 2026-11-20: Paper 1.21.11 API has org.bukkit.event.inventory.HopperInventorySearchEvent (SOURCE/DESTINATION, hopper block + search block, setInventory) and Hopper.setTransferCooldown; it fires only for hopper blocks (not hopper minecarts, not droppers). Design: the listener does the one-item move itself in the search event as one DeviceService.edit and sets the 8-tick cooldown; the event inventory stays null (no virtual inventory). Composter keeps its own PAR-PROC-11 rule (0 slots: output is a count)."
  - "Done: PAR-INFRA-60 commit. core/device/automation (Face, PartialSlots, SideTable, TfcSideTables, AutomationRules), paper/device/automation (DeviceAutomation registration API, HopperMover, HopperAutomationListener, DeviceAutomationModule `device_automation`: barrel, firepit, grill, pot, quern, powder keg). Tests: TfcSideTablesTest (8), HopperAutomationMockBukkitTest (9) green; itest DeviceAutomationCase added to SelfTestSuite (Pending server run on Paper and Folia). Pending in-game check (in_game_steps; docs/verification/PAR-INFRA-60/). Divergences in the ledger; follow-up PAR-INFRA-63 (droppers, hopper minecarts, screen-aware moves, barrel settle, config.yml keys)."
tests:
  - "core: side tables golden vs the TFC constructors (barrel all six facings, firepit, grill, pot, quern, composter, powderkeg)"
  - "paper MockBukkit: a HopperInventorySearchEvent into a barrel inserts the stack into SLOT_ITEM from above and the fluid-container slot from a side; extraction from the composter below; automation off leaves the event untouched"
  - "itest DeviceAutomationCase on Paper and Folia: a real hopper feeds a quern and empties it"
done_criteria:
  - "Hoppers feed and empty the done devices on TFC's sides; the off switch disables it; golden and MockBukkit tests green; itest passes on Paper and Folia"
  - "Screenshots and verdict in docs/verification/PAR-INFRA-60/; ledger divergence rows updated (automation part)"
---

# PAR-INFRA-60: Device automation through hoppers by side

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
