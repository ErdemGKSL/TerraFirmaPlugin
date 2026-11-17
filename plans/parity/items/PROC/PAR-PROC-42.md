---
id: PAR-PROC-42
title: "Barrel world rules: redstone seals and unseals (barrelEnableRedstoneSeal), items dropped into an open upright barrel are gathered"
area: PROC
status: open
wave: 18
deps: [PAR-PROC-08, PAR-PROC-35, PAR-PROC-38, PAR-INFRA-10]
size: S
aliases: ["parity audit 2026-11-17: ledger divergence 'TFC seals and unseals a barrel on a redstone signal (barrelEnableRedstoneSeal), gathers items dropped into an open barrel ...' (revisit: 'redstone, gathering and automation follow-ups'); PAR-PROC-35 and PAR-PROC-38 took the rain fill, the rack and the tap, nobody took these two"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java"
  - "src/main/java/net/dries007/tfc/util/Helpers.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/barrel/world/**"
  - "paper/src/test/java/dev/tfp/paper/barrel/world/**"
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelDevice.java"
  - "core/src/main/java/dev/tfp/core/barrel/BarrelRedstone*.java"
  - "core/src/main/java/dev/tfp/core/barrel/BarrelData.java"
  - "core/src/test/java/dev/tfp/core/barrel/BarrelData*Test.java"
  - "core/src/test/java/dev/tfp/core/barrel/BarrelRedstone*Test.java"
  - "docs/verification/PAR-PROC-42/**"
ui: true
in_game_steps:
  - "op Bot (debug commands from client chat). tfp debug place tfp:wood/barrel/oak; tfp debug barrel fill 1000 tfp:brine"
  - "place a lever next to the barrel and flip it on: the barrel shows the sealed model, tfp debug barrel reads sealed; flip it off: unsealed; screenshot both"
  - "seal it by hand (sneak + empty hand) with the lever off, then flip the lever on and off: TFC's POWERED edge rule (on with a sealed barrel only records the signal, off then unseals)"
  - "with the barrel open and upright, Bot drops 4 tfp:food/beef onto it: within 1 s the item slot holds them and no item entity is left; drop onto a sealed barrel: the items stay on top"
plan:
  - "TFC: BarrelBlock.neighborChanged -> SealableDeviceBlock.handleNeighborChanged when barrelEnableRedstoneSeal (default true): on a change of level.hasNeighborSignal against the POWERED property, set POWERED and, when the signal differs from SEALED, set SEALED to the signal and run onSeal/onUnseal. BarrelBlockEntity.serverTick every 20 game ticks, when unsealed and facing UP, gathers item entities in the box (0.25, 0.0625, 0.25)-(0.75, 0.9375, 0.75) into SLOT_ITEM through Helpers.gatherAndConsumeItems."
  - "Redstone: our CE barrel has no powered property; keep the last signal in the barrel record (BarrelData field, codec migration) and decide the edge in a pure core/barrel/BarrelRedstone rule (golden vs handleNeighborChanged's table). The trigger is the neighbour-update runner (PAR-INFRA-10: decide async, apply sync) through DeviceService.neighbourChanged -> the barrel handler; the seal runs BarrelDevice's existing toggleSeal path (sounds, on_seal/on_unseal, block state). Sideways barrels on racks follow the same rule."
  - "Gathering without a per-block tick: on item spawn/drop events schedule an entity task on the item (every 20 ticks while it moves, at most 10 checks after it rests); when it lies in an open upright barrel's box, one DeviceService.edit inserts what SLOT_ITEM accepts (TFC isItemValid and size rules) and shrinks the entity. Share the entity-check helper with PAR-HEAT-30 only by copying a small class (no cross-item file ownership) or by a later refactor item."
  - "barrelEnableRedstoneSeal keeps TFC's default here; its config.yml key is PAR-DATA-06's. Ledger: shrink the barrel divergence row to the automation part (PAR-INFRA-60) and the fluid sides."
tests:
  - "core: BarrelRedstone edge table golden vs SealableDeviceBlock.handleNeighborChanged (all four POWERED x SEALED x signal cases)"
  - "paper MockBukkit: a powered neighbour seals an open barrel and unpowering unseals it; a dropped item over an open barrel is gathered, over a sealed or sideways one it is not"
done_criteria:
  - "A lever or any redstone signal seals and unseals a barrel with TFC's edge rule; items dropped into an open upright barrel go into its item slot"
  - "./gradlew check passes; screenshots and verdict in docs/verification/PAR-PROC-42/"
---

# PAR-PROC-42: Barrel world rules: redstone seal and dropped-item gathering

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
