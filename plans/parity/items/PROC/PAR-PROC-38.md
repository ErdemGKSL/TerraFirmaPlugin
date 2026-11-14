---
id: PAR-PROC-38
title: "Barrel rack, sideways barrel and tap pouring, and the in-game barrel check"
area: PROC
status: open
wave: 14
deps: [PAR-PROC-35]
size: L
aliases: ["follow-up of PAR-PROC-35 (plan steps 5 and 6: the rack, the facing/rack states, the tap and the screenshot check did not fit; PROTOCOL 3a + 6.5)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelRackBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java"
  - "src/main/resources/assets/tfc/blockstates/barrel_rack.json"
  - "src/main/resources/assets/tfc/models/block/barrel_rack.json"
  - "src/main/resources/assets/tfc/blockstates/wood/barrel/oak.json"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelDevice.java"
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelModule.java"
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelScreenLink.java"
  - "paper/src/main/java/dev/tfp/paper/barrel/PaperBarrelEffects.java"
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelEffects.java"
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelRack*.java"
  - "paper/src/test/java/dev/tfp/paper/barrel/**"
  - "core/src/main/java/dev/tfp/core/barrel/**"
  - "core/src/test/java/dev/tfp/core/barrel/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/barrel/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/barrel/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/barrelrack/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/barrelrack/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "content/generated/content/craftengine/tfp/configuration/barrel/**"
  - "docs/verification/PAR-PROC-38/**"
ui: true
in_game_steps:
  - "tfp debug barrelgui 5000 (the screen alone): hover the tank; press the seal button; screenshot sealed (the tank shots are PAR-PROC-09's)"
  - "tfp debug place tfp:wood/barrel/oak"
  - "fill half (5 water buckets); right-click; screenshot GUI and block (the tinted fluid surface)"
  - "check that each water bucket reached the record (PAR-PROC-08 transfer path; the PAR-PROC-09 run saw an empty bucket and nothing drawn): /tfp debug barrel on the barrel (PAR-PROC-35), not /tfp debug barrelgui (a demo barrel); /tfp debug barrel fill 5000 shows the surface without buckets"
  - "make it rain by the climate (PAR-CLIM-12 couples /weather; the climate decides, /weather rain alone may not): leave the barrel unsealed under the sky, right-click it twice 400 ticks apart while it rains; /tfp debug barrel: the tank gained 100 mB"
  - "place tfp:barrel_rack, then a barrel on it (sideways); screenshot; the tap drains into a container below"
plan:
  - "Deferred from PAR-PROC-35 (PROTOCOL 3a + 6.5). PAR-PROC-35 wired the block to the barrel screen (`BarrelScreenLink`, `DeviceService.edit` / `hook`), the fluid IO slots and the lazy rain fill (`core/barrel/BarrelRain`, `BarrelDevice.settle`, upright assumed: `settle(..., upright = true)`), the fluid surface display (`PaperBarrelEffects.surface`, pack model `tfp:render/barrel_fluid`) and `/tfp debug barrel [fill <mB> [fluid]]`."
  - "1. Barrel rack: `tfp:barrel_rack` CE block (TFC `BarrelRackBlock`: bottom-supported, RACK_SHAPE hitbox demand) from `datagen/barrelrack`; the barrel's `facing` (up or a side) and `rack` states with the hitbox rows `tfc:wood/barrel[axis=x|z,rack=false|true]` pinned in GeneratedCarrierTest; the sideways models (TFC blockstates) as entity_renderer rotations."
  - "2. Placement and use (TFC `getStateForPlacement`, `useItemOn`): placing a barrel on a rack lays it sideways facing the player; a rack item on a sideways barrel racks it; sneak with an empty hand on the rack's top takes the rack back; breaking a racked barrel leaves the rack (`onDestroyedByPlayer`) and `onRemove` returns the rack item; a sideways barrel needs sturdy support below. The facing goes into `BarrelDevice.settle` (no rain on a sideways barrel; the surface hidden)."
  - "3. Tap (TFC `tickPouring`): an unsealed sideways barrel with fluid pours 1 mB per tick through the tap (`pos.relative(facing)` must be air) into the fluid handler below the tap (another barrel, a TFC fluid block entity), lazily: decide async, apply sync, credited at accesses of either device; TFC's drip sound every 12 ticks becomes an action-only sound."
  - "4. In-game steps above (cheap-model agent, the main session judges): store crops and verdict.md in docs/verification/PAR-PROC-38/."
tests:
  - "datagen: barrel rack entry, the facing/rack states and their carriers pinned in GeneratedCarrierTest"
  - "paper: placing on a rack, racking, unracking, breaking a racked barrel; a sideways barrel takes no rain; the tap pours lazily into a barrel below (lazy vs stepped)"
done_criteria:
  - "Screenshot of the barrel GUI opened from the block and a half-full barrel showing liquid (moved from PAR-PROC-09 via PAR-PROC-35); the bucket water reaches the tank"
  - "Rain fills an open barrel in game; a racked barrel pours from its tap"
---

# PAR-PROC-38: Barrel rack, sideways barrel and tap pouring, and the in-game barrel check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
