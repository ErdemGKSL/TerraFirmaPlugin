---
id: PAR-PROC-41
title: "Sealed barrel item keeps its contents: breaking a sealed barrel drops the barrel with its items and fluid (TFC BarrelComponent), placing it restores them"
area: PROC
status: blocked
wave: 15
deps: [PAR-PROC-08, PAR-PROC-35, PAR-INFRA-04]
size: M
aliases: ["follow-up of PAR-INFRA-50 (2026-11-17): the barrel contents tooltip has nothing to show because a broken barrel always spills (ledger divergence 'TFC keeps a sealed barrel's contents on its dropped item')"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/component/block/BarrelComponent.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelDevice.java"
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelItemContents.java"
  - "paper/src/test/java/dev/tfp/paper/barrel/BarrelItemContentsMockBukkitTest.java"
ui: false
plan:
  - "Blocked (2026-11-17): needs files outside files_owned. DeviceKind.removal is one RemoveBehaviour per kind and DeviceService.removed reads kind.removal() once, so a sealed barrel cannot SAVE while an unsealed one DROPs; SAVE for the whole kind would also stamp an unsealed barrel's record (slots already ejected) onto its item, duplicating items. The smallest change: a per-record removal in paper/src/main/java/dev/tfp/paper/device/DeviceKind.java (PAR-INFRA-12; e.g. Builder.removal(Function<DeviceRecord<S>, RemoveBehaviour>), default constant) and DeviceService.removed using it on the caught-up record. With that, BarrelDevice picks SAVE when sealed, DROP otherwise, and the existing SAVE path (ItemSpawnEvent stamp, fromItem on placement) restores the sealed record; the sealed item's VERY_HEAVY weight also needs paper/src/main/java/dev/tfp/paper/itemsize/ItemSizes.java. Unblock by widening files_owned to those files or by an INFRA item adding the per-record removal first."
  - "Now: BarrelDevice removes with DeviceKind.RemoveBehaviour.DROP for every barrel, so a sealed barrel spills its slots and loses its fluid. TFC (SealableDeviceBlock.beforeRemove, BarrelBlockEntity.collectImplicitComponents) ejects only an unsealed barrel; a sealed one drops its block item with BarrelComponent (items, fluid, the seal tick and the recipe) and placing that item restores the barrel sealed."
  - "Do: a per-state removal for the barrel kind (sealed: drop the barrel item carrying ContentsPdc for the slots, FluidPdc for the tank, and the seal record; unsealed: as now). On placement of a barrel item with that data, restore the record (sealed, its contents, the seal tick) before the first access. If the device framework needs a hook it lacks, record it here and add the smallest change in a plan note before editing (DeviceKind belongs to PAR-INFRA-12)."
  - "Then drop or reword the ledger divergence 'TFC keeps a sealed barrel's contents on its dropped item'; the sealed barrel item's weight VERY_HEAVY comes with it (TFC getWeight)."
tests:
  - "paper: BarrelItemContentsMockBukkitTest (sealed barrel broken -> one barrel item with the slots and fluid; placed again -> same record, still sealed, recipe progress continues lazily; unsealed barrel still spills)"
done_criteria:
  - "Breaking and placing a sealed barrel keeps its items, fluid and seal as TFC does; tests green; ledger updated"
---

# PAR-PROC-41: Sealed barrel item keeps its contents

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
